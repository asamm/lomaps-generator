import osmium
from osmium.osm import Tag

from tourist import consts


# class TagC(NamedTuple):
#     """ A single OSM tag.
#     """
#     k: str
#     "Tag key"
#
#     v: str
#     "Tag value"
#
#     def __str__(self) -> str:
#         return f"{self.k}={self.v}"


class TagListC():
    """
    Class to store tags
    """

    from typing import Union, List, Dict

    def __init__(self, tags: Union[List[Tag], Dict[str, str]]):
        # by type of object create a dictionary of tags
        if isinstance(tags, list):
            self.tags = {t.k: t.v for t in tags}
        else:
            # create a deep copy of the dictionary to be a new instance of the dictionary
            self.tags = tags

    # prepare method to get tag value by key or return defined default value
    def get(self, key, default=None):

        # return value of the key or default value
        return self.tags.get(key, default)

    def add_tags(self,tags: Union[List[Tag], Dict[str, str]]):
        """
        Add a tag or multiple tags to the tag list if keys exist, they are updated
        :param tag: a single Tag or a list of Tags
        """

        # by type of object create a dictionary of tags
        if isinstance(tags, list):
            self.tags.update({t.k: t.v for t in tags})
        else:
            # create a deep copy of the dictionary to be a new instance of the dictionary
            self.tags.update({k: v for k, v in tags.items()})

    def add(self, key, value):
        """
        Add a single tag to the tag list if the key exists, it is updated
        :param key: tag key
        :param value: tag value
        """
        self.tags[key] = value

    def merge(self, tag_list):
        """
        Merge another TagListC into this one
        :param tag_list: TagListC to merge
        """
        self.tags.update(tag_list.tags)

    def has_key(self, key):
        """
        Check if a tag with the given key exists
        :type key: str key to check
        """
        return key in self.tags

    def remove(self, key):
        """
        Remove a tag with the given key
        :type key: str key to remove
        """
        if self.has_key(key):
            del self.tags[key]


    def __iter__(self):
        return iter(self.tags)

    # prepare to string
    def __str__(self) -> str:
        return ", ".join(str(tag) for tag in self.tags)

    def __len__(self):
        return len(self.tags)

class NodeC:
    """
    Class to store nodes
    """

    def __init__(self, node: osmium.osm.Node, tag_list: TagListC = None):
        """
        Initialize the class with a node. It stores the node's ID and tags.
        :type node: object
        """
        self.id = node.id
        self.x = node.location.x
        self.y = node.location.y
        if (tag_list):
            self.tags_list = tag_list
        else:
            self.tags_list = TagListC(node.tags)

class WayC:
    """
    Class to store ways
    """

    def __init__(self, way: osmium.osm.Way, parent_relation_id: int = None):
        """
        Initialize the class with a way. It stores the way's ID and tags.
        :type way: object
        """
        self.id = way.id
        self.parent_relation_id = parent_relation_id
        self.tags_list = TagListC(way.tags)
        self.node_ids = [node.ref for node in way.nodes]


class MemberC:
    """
    Class to store relation members
    """

    def __init__(self, member: osmium.osm.RelationMember):
        """
        Initialize the class with a relation member. It stores the member's reference and type.
        :type member: object
        """
        self.ref = member.ref
        self.type = member.type
        self.role = member.role

    def is_role_link(self):
        """
        Check if the role of the member is 'link'
        :return: True if the role is 'link'
        """
        return self.role is not None and self.role == 'link'


class RelationC:
    def __init__(self, rel: osmium.osm.Relation):
        """
        Initialize the class with a relation. It stores the relation's ID, tags, and members.
        :type rel:
        """
        self.id = rel.id
        # test if key is in list of tourist_route_cached_keys

        self.tags = TagListC({tag.k: tag.v for tag in rel.tags if tag.k in consts.tourist_route_cached_keys})
        self.members = [MemberC(member) for member in rel.members]

        # Check network tag, osmc:symbol tag
        from tourist.helpers import validate_fix_network_tag
        validate_fix_network_tag(self)

        # parse osmc:symbol tag
