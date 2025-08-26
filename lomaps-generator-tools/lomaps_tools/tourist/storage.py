import logging

from osm.osm_entity import TagListC, RelationC, NodeC


class DataStorage():

    def __init__(self):

        self.relations = {}  # Store all tourist relations {id: RelationC}
        self.way_tag_list = {}  # Store tags of ways that are part of relations. Key: way ID, Value: list of tags
        self.nodes = {}  # Store network tags of nodes

        self.way_nodes = [] # Store nodes of ways. Used only for testing purposes

    ## RELATIONS

    def add_relation(self, relation: RelationC):
        self.relations[relation.id] = relation

    def get_relation(self, relation_id):
        return self.relations.get(relation_id, None)

    def is_relation_cached(self, relation_id):
        return relation_id in self.relations

    ## WAY TAGS

    def add_way_tags(self, way_id, tags):

        # create a copy for every new way added to cache
        logging.debug(f"Adding tags for way into cache {way_id}")
        tag_list = TagListC({k: v for k, v in tags.tags.items()})

        if way_id not in self.way_tag_list:
            self.way_tag_list[way_id] = [tag_list]
        else:
            # get already stored tags for this way
            tags_array = self.way_tag_list[way_id]
            # check if the same tags are not already stored
            if tag_list not in tags_array:
                tags_array.append(tag_list)
        return self.way_tag_list[way_id]

    def get_way_tags(self, way_id):

        return self.way_tag_list.get(way_id, None)

    def get_all_way_ids(self):
        return self.way_tag_list.keys()

    ## NODES

    def add_node(self, node: NodeC):
        self.nodes[node.id] = node

