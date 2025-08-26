import logging

import osmium
from osm.osm_entity import RelationC, WayC
from osm.osm_writer import OsmWriter
from osmium.osm import Way
from tourist import helpers
from tourist.helpers import is_tourist_relation, is_valid_type_or_state, is_valid_supperroute, \
    is_hiking_network_node, is_cycling_network_node
from tourist.storage import DataStorage


class TouristRouteHandler(osmium.SimpleHandler):
    """
    Handler to find tourist relations and their child way IDs.
    """

    def __init__(self, data_storage: DataStorage):

        super().__init__()

        self.data_storage = data_storage


    def relation(self, rel):
        # logging.debug(f"Read relation id={rel.id}")
        # Check if the relation has a tag route=hiking, cycling, skiing, etc.
        if is_tourist_relation(rel.tags) and is_valid_type_or_state(rel) and is_valid_supperroute(rel):
            # Store the need to create a new light-weight relation object because reference
            # to the original relation object streamed by osmium is lost
            self.data_storage.add_relation(RelationC(rel))
            logging.debug(f"Relation id={rel.id} is a tourist route. Added to the storage")

    # def node(self, node):
    #
    #     if is_network_node(node.tags):
    #         logging.debug(f"Node id={node.id} is a network node. Tags: {node.tags}")
    #         node_tag_list = TagListC({tag.k: tag.v for tag in node.tags if tag.k in consts.network_node_keys})
    #         self.data_storage.add_node(NodeC(node, node_tag_list))

    def post_process(self):

        # Iterate over all relations to find ways that are part of the relations
        for relationC in self.data_storage.relations.values():
            self.process_relation(relationC)

    def process_relation(self, rel: RelationC, parent_ids=[], parent_tags=None):
        """
        Process a relation and its members to find ways that are part of the relation
        :param rel: RelationC object
        """
        # Recursively find ids of ways those are members of a relation or member of a sub-relations
        if parent_ids is None:
            parent_ids = []
        logging.debug(f"Processing relation id={rel.id}")
        for memberC in rel.members:
            if memberC.type == 'w':  # Way
                # Store tags of ways that are part of relations
                if parent_tags is not None:
                    # set tags of parent relation to the way
                    self.data_storage.add_way_tags(memberC.ref, parent_tags)
                else:
                    self.data_storage.add_way_tags(memberC.ref, rel.tags)

            elif memberC.type == 'r':  # Sub-relation
                if memberC.ref in parent_ids:
                    logging.warning(
                        f"Relation id={rel.id} has a circular reference to itself or one of its parents. When parent Ids: {parent_ids}")
                    continue  # Skip self-references
                if memberC.is_role_link():
                    # skip children relation that are type link
                    # Connection is used for routes linking two different routes or linking a route eq. with a village centre.
                    continue

                # get relation object by member id
                child_rel = self.data_storage.get_relation(memberC.ref)

                if child_rel is not None:
                    if helpers.is_child_the_same_style_as_parent(rel, child_rel):
                        # skip this child relation because it is the same style as the parent relation (and parent has name)
                        continue

                    parent_ids.append(rel.id)
                    self.process_relation(child_rel, parent_ids, rel.tags)

class WayHandler(osmium.SimpleHandler):
    """
        Handler to get data of ways that are members of tourist relations
        Also updates node network junctions
    """

    def __init__(self, options, data_storage: DataStorage, osm_writer: OsmWriter):
        """
        :param way_ids: id of ways to obtain from the OSM file
        """
        super().__init__()
        self.options = options
        self.data_storage = data_storage
        self.osm_writer = osm_writer

    def way(self, way):
        self.process_way(way)

    def process_way(self, way: Way):

        if way.id in self.data_storage.get_all_way_ids():

            wayC = WayC(way)

            # If the way is part of a tourist route, process it
            tags_array = self.prepare_tourist_tags(wayC)

            # Write the way to the output file
            # get nodes ids of orig way
            node_ids = [node.ref for node in way.nodes]
            for tag_list in tags_array:
                tag_list.remove('type')
                self.osm_writer.add_new_way(self.options.wayid, node_ids, tag_list)
                self.options.wayid += 1

            if self.options.addwaynodes:
                # Testing option that adds original nodes of the way to the output file. Store nodes ids of tourist ways
                self.data_storage.way_nodes.extend([node_ref.ref for node_ref in way.nodes])


    def prepare_tourist_tags(self, way: WayC):
        """
        Combine tags from tourist relations with original tags of the way, parse osmc tags and create osmc_order
        :param way: WayC object with original way definition
        """
        # get tags of relation the way is part of
        relations_tags_array = self.data_storage.get_way_tags(way.id)

        # merge ref and name tags (because presentation purposes in Mapsforge LoMaps)
        helpers.merge_ref_and_name(relations_tags_array)

        # create osmc_color, foreground, background, etc. tags
        helpers.parse_and_create_osmc_tags(relations_tags_array)

        # copy ways original tags to tags from relations
        helpers.copy_original_tags(way, relations_tags_array)

        helpers.highway_tag_to_locus(relations_tags_array)

        helpers.copy_additional_relation_tags(relations_tags_array)

        return relations_tags_array


class NetworkNodeUpdater:

    def __init__(self, data_storage: DataStorage, osm_writer: OsmWriter):
        self.data_storage = data_storage
        self.osm_writer = osm_writer


    def write_node_junctions(self):
        """
        Write junction nodes to the output file
        :param node_tags: dictionary with node tags
        """
        for nodeC in (self.data_storage.nodes.values()):

            if is_hiking_network_node(nodeC.tags_list):
                nodeC.tags_list.add('hiking_node', 'NLBE')
            if is_cycling_network_node(nodeC.tags_list):
                nodeC.tags_list.add('cycle_node', 'NLBE')

            self.osm_writer.add_new_node(nodeC.id, nodeC.x, nodeC.y, nodeC.tags_list)

            # TODO Check if the original node id can be used for older Mapsforge LoMaps in former implementation
            # were hiking and cycling ref nodes seperated into new nodes with new id but it should not be necessary

            # if is_hiking_network_node(nodeC.tags_list):
            #     nodeC.tags_list.add('hiking_node', 'NLBE') #because the Mapsforge LoMaps uses this tag to identify network nodes
            #     self.osm_writer.add_new_node(self.options.nodeid, nodeC.x, nodeC.y, nodeC.tags_list)
            #     self.options.nodeid += 1
            #
            # if is_cycling_network_node(nodeC.tags_list):
            #     nodeC.tags_list.add('cycle_node', 'NLBE')
            #     self.osm_writer.add_new_node(self.options.nodeid, nodeC.x, nodeC.y, nodeC.tags_list)
            #     self.options.nodeid += 1

class WayNodeHandler(osmium.SimpleHandler):

    def __init__(self, data_storage: DataStorage, osm_writer: OsmWriter):
        super().__init__()
        self.data_storage = data_storage
        self.osm_writer = osm_writer

    def node(self, node):
        if node.id in self.data_storage.way_nodes:
            #("Add node to the output file: ", node.id)
            self.osm_writer.add_node(node)