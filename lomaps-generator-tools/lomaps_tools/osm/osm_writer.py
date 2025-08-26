import os

import osmium
from osm.osm_entity import TagListC
from osmium.osm import mutable, Location


class OsmWriter(osmium.SimpleWriter):
    def __init__(self, filename):
        # check if file exist and remove it
        if os.path.exists(filename):
            os.remove(filename)

        # create folder for export if not exist
        os.makedirs(os.path.dirname(filename), exist_ok=True)

        super().__init__(filename)

    def add_new_node(self, id, x, y, tag_list: TagListC):
        # Create a new mutable node
        node = osmium.osm.mutable.Node(id = id, tags=[(k, str(v)) for k, v in tag_list.tags.items()])

        # Set the attributes for the new node
        node.location = Location(x/1e7, y/1e7) # Osmium uses fixed-point coordinates, so divide by 1e7
        node.visible = True

        # Add the node to the file
        self.add_node(node)

    def add_new_way(self, id, node_ids, tag_list: TagListC):
        # Create a new way
        w = osmium.osm.mutable.Way(id=id, tags=[(k, str(v)) for k, v in tag_list.tags.items()])

        w.nodes = [osmium.osm.NodeRef(Location(0, 0), node_id) for node_id in node_ids]

        # Write the new way
        self.add_way(w)

    def close_writer(self):
        # Close the writer to finalize the OSM file
        self.close()
