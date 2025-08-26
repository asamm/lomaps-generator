import logging
import os
import subprocess
from typing import Union

from osm.osm_entity import TagListC, RelationC, WayC
from osmium.osm import Relation
from tourist import consts


### RELATION FUNCTIONS ###
def is_valid_supperroute(rel: Relation):
    """
     The international trails are often organized into so-called super route relation
     https://wiki.openstreetmap.org/wiki/Relation:superroute
     This super route may have different color then national routes. For example international E8 route has blue
     color but local national routes use RED marked trails for it. If super route isn't removed the Blue a Red color
     is printed in the map
    :param rel: relation object to check
    :return: true if the relation is a valid super route
    """
    if rel.tags.get('type', '') == 'superroute':
        if rel.tags.get('osmc:symbol') and is_network_valid(rel):
            return True
        else:
            logging.info(f"Relation id={rel.id} is not a valid superroute")
    return True


def is_valid_type_or_state(rel):
    if rel.tags.get('route', '') in {"disused_route", "disused:route"}:
        # route is not "active" remove it from processed way
        logging.debug(f"Relation id={rel.id} is disused route. Do not process it as tourist route")
        return False

    # check if the state is planned, disused, etc.
    if rel.tags.get('state', '') in consts.invalid_states_for_tourist_route:
        logging.debug(f"Relation id={rel.id} is not active route. Do not process it as tourist route")
        return False

    if rel.tags.get('type', '') in consts.invalid_types_for_tourist_route:
        logging.debug(f"Relation id={rel.id} is not valid type of route. Do not process it as tourist route")
        return False
    return True


def is_tourist_relation(tags):
    """
    Check if the relation is a tourist relation
    :param tags: tags of the relation
    :return: true if the relation is a tourist relation
    """
    return (is_bicycle_relation(tags) or is_mtb_relation(tags) or is_hiking_relation(tags)
            or is_ski_relation(tags))


def is_bicycle_relation(tags):
    return tags.get('route') == 'bicycle'


def is_mtb_relation(tags):
    return tags.get('route') == 'mtb'


def is_hiking_relation(tags):
    return tags.get('route') == 'hiking' or tags.get('route') == 'foot'


def is_ski_relation(tags):
    return tags.get('route') == 'ski' or tags.get('route') == 'piste'


def is_network_valid(rel: Union[Relation, RelationC]):
    """
    Check if the network tag is valid for the relation
    :param rel: relation object to check
    :return:
    """
    network = rel.tags.get('network', '')
    if is_bicycle_relation(rel.tags) or is_mtb_relation(rel.tags):
        return network in consts.bycicle_network_type.keys()
    if is_hiking_relation(rel.tags):
        return network in consts.hiking_network_type.keys()

    return False


def is_network_node(tags):
    """
    Check if the node is a network node (Junctions in NL, BE)
    :param tags: tags of the node
    :return: true if the node is a network node
    """
    for key in consts.network_node_keys:
        return key in tags

def is_cycling_network_node(tag_list: TagListC):
    """
    Check if the node is a cycling network node (Junctions in NL, BE)
    :param tags: tags of the node
    :return: true if the node is a cycling network node
    """
    return 'rcn_ref' in tag_list

def is_hiking_network_node(tag_list: TagListC):
    """
    Check if the node is a hiking network node (Junctions in NL, BE)
    :param tags: tags of the node
    :return: true if the node is a hiking network node
    """
    return 'rwn_ref' in tag_list


def is_child_the_same_style_as_parent(rel, child_rel):
    """
    Check if the child relation has the same style as the parent relation. Also if parent relation is a superroute
    and has defined name
    :param rel: parent relation
    :param child_rel: child relation
    :return: True if parent releation is supperroute, has defined name and has the same style as the child relation
    """
    if rel.tags.get('type', '') == 'superroute' and rel.tags.get('name'):
        return rel.tags.get('osmc:symbol') == child_rel.tags.get('osmc:symbol')


def validate_fix_network_tag(rel: RelationC):
    """
    Validate the relation and fix potential mapping mistakes in the network tag
    :param rel: relation object to validate
    :return:
    """

    if is_bicycle_relation(rel.tags) or is_mtb_relation(rel.tags):

        if rel.tags.get('network', '') == '':
            logging.info(f"[warn_cyc_001] Relation id={rel.id} has empty network tag. Set new value network=lcn")
            rel.tags.add('network', "lcn")
        elif not is_network_valid(rel):
            logging.info(f"[warn_cyc_002] Relation id={rel.id} has wrong network tag: "
                         f"network={rel.tags.get('network', '')}. Set new network value lcn")
            rel.tags.add('network', 'lcn')
        return

    elif is_hiking_relation(rel.tags):
        if rel.tags.get('network', '') == '':
            logging.info(f"[warn_hike_001] Relation id={rel.id} has empty network tag. Set new value network=lwn")
            rel.tags.add('network', "lwn")
        elif not is_network_valid(rel):
            logging.info(f"[warn_hike_002] Relation id={rel.id} has wrong network tag: "
                         f"network={rel.tags.get('network', '')}. Set new network value lwn")
            rel.tags.add('network', 'lwn')
        return

### WAY TAGS PARSING FUNCTIONS ###

def get_color_from_osmc(osmcsymbol):
    """
    Obtain the first value from osmc:symbol tag. Check value in list of possible colours if color is supported
    :param osmcsymbol: value of osmc:symbol tag
    :return: color from osmc:symbol tag or None if color is not supported
    """
    position = osmcsymbol.find(":")
    if position == -1:
        # value has no semicolon for this reason try to validate whole value of osmcsymbol string
        if is_osmc_color_valid(osmcsymbol):
            # value is valid color
            return osmcsymbol
        return None

    return osmcsymbol[:position]


def is_osmc_color_valid(color):
    """
    Check if the color is valid color from osmc:symbol tag
    :param color:  value of osmc:symbol tag to check
    :return: True if the color is valid
    """
    return color in consts.hiking_colour_type


def get_color_from_color_tags(tags: TagListC):
    """
    Try to get color from "colour" or "color" tags
    :param tags:
    """
    color = ""
    if tags.has_key('colour'):
        color = tags.get('colour')
    elif tags.has_key('color'):
        color = tags.get('color')
    return color


def parse_osmc_symbol(osmcsymbol: str):  # -> TagListC:
    tags_created = TagListC([])

    if not osmcsymbol:
        return tags_created

    tags_created.add('osmc','yes')

    position = osmcsymbol.find(":")
    if position == -1:
        # osmcsymbol has no semicolon ":" for this reason try to validate the whole value of osmcsymbol string
        if is_osmc_color_valid(osmcsymbol):
            tags_created.add('osmc_color', osmcsymbol)
        return tags_created

    tokens = osmcsymbol.split(":")
    for i, token in enumerate(tokens):
        if i == 0:
            if is_osmc_color_valid(token.lower()):
                tags_created.add('osmc_color', token)
            elif token:
                logging.debug(f"Invalid osmc color: {token}")
        elif i == 1:
            tags_created.add('osmc_background', token)
        elif i == 2:
            tags_created.add('osmc_foreground', token)
        elif i == 3:
            if len(token) <= 5:
                tags_created.add('osmc_text_length', len(token))
                tags_created.add('osmc_text', token)
        elif i == 4:
            if is_osmc_color_valid(token.lower()):
                tags_created.add('osmc_text_color', token)

    return tags_created


def merge_ref_and_name(tag_array):
    """
    For Mapsforge LoMaps is needed to process all string function - in this case join ref and name tags

    """

    for tag_list in tag_array:
        ref = tag_list.get('ref', '')
        name = tag_list.get('name', '')

        if len(name) > 0:
            if len(ref) > 0 and ref not in name:
                # append ref to the existing name
                tag_list.add('name', f"{name}, {ref}")
        elif len(ref) > 0 and ref not in name:
            # name is empty, replace it with ref value
            tag_list.add('name', ref)


def copy_original_tags(way: WayC, tags_array):
    """
    Copy tags from the original way to the tags from the relation
    :param way: original way
    :param tags_array: tags from the tourist relations way is part of
    """
    for tag_orig in way.tags_list:
        key = tag_orig.k
        if key in consts.key_inherited_from_orig_way_keys:
            for tag_list in tags_array:
                if not tag_list.has_key(key):
                    tag_list.add(tag_orig.k, tag_orig.v)
                else:
                    logging.debug(f"Tag {key} already exists in the relation tags. For original way {way.id} ")


def highway_tag_to_locus(tags_array):
    """
    If tag_list contains highway tag, change it to lm_highway and duplicated it as osmc_highway
    :param tags_array: array of tag list to process it
    """
    for tag_list in tags_array:
        if tag_list.has_key('highway'):
            tag_list.add('osmc_highway', tag_list.get('highway'))
            tag_list.add('lm_highway', tag_list.get('highway'))
            tag_list.remove('highway')

def parse_and_create_osmc_tags(tags_array: [TagListC]):
    """
    Parse osmc tags and create osmc_order
    """

    # iterate over all tags of the way and create osmc tags
    for tag_list in tags_array:

        osmc_tag_list = parse_osmc_symbol(tag_list.get('osmc:symbol', ''))

        if not osmc_tag_list.has_key('osmc_color') or osmc_tag_list.get('osmc_color') == '':
            color = get_color_from_color_tags(tag_list)
            if color is not None:
                osmc_tag_list.add('osmc_color', color)
        # merge created osmc tags with original tags from relation
        tag_list.merge(osmc_tag_list)

        # For Mapsforge LoMaps remove foreground if it is the same as background color
        if tag_list.has_key('osmc:foreground') and tag_list.has_key('osmc:background'):
            if tag_list.get('osmc:foreground').startswith(tag_list.get('osmc:background')):
                logging.debug(
                    f"Remove foreground color {tag_list.get('osmc:foreground')} from way {tag_list.get('osmc:symbol')}")
                tag_list.remove('osmc:foreground')

    # prepare order for symbols and for offset of colors
    compute_osmc_order(tags_array)

def compute_osmc_order(tag_list_array: [TagListC]):
    tag_list_type_map = {
        "hiking": [],
        "cycling": [],
        "ski": []
    }

    # organize tags by type
    for tag_list in tag_list_array:
        if is_hiking_relation(tag_list):
            tag_list_type_map["hiking"].append(tag_list)
        # elif is_bicycle_relation(tags) or is_mtb_relation(tags):
        #     tag_list_type_map["cycling"].append(tags)
        # elif is_ski_relation(tags):
        #     tag_list_type_map["ski"].append(tags)

    for t, tourist_tags in tag_list_type_map.items():
        colors_for_way = {}  # dic of unique osmc colors for specific type of tourist route and it's order
        osmc_foregrounds_for_way = {}  # dic of unique osmc foregrounds icons for specific type of tourist route

        # check if there is any iwn/rwn...without defined OSMC color
        for tag_list in tourist_tags:
            if tag_list.get('network') in consts.hiking_network_type.keys() and not tag_list.get('osmc_color'):
                # this is any local or national, international hiking route without defined osmc color set it
                # as first order and simulate it as red color (another red color will have the same order as this one)
                colors_for_way[consts.hiking_fallback_colour] = len(colors_for_way)
                break

        for tag_list in tourist_tags:

            # check if such color and foreground is already in any other tag_list if not increase the order
            osmc_color = tag_list.get('osmc_color', '')
            osmc_foreground = tag_list.get('osmc_foreground', '')

            if osmc_color not in colors_for_way and osmc_color != '':
                # such color isn't in list of used colors, increase the order
                colors_for_way[osmc_color] = len(colors_for_way)

            if osmc_foreground not in osmc_foregrounds_for_way and osmc_foreground != '':
                # such symbol isn't in list of used osmc foregrounds
                osmc_foregrounds_for_way[osmc_foreground] = len(osmc_foregrounds_for_way)

            # get index of osmc_color in the list of colors
            osmc_order = colors_for_way[osmc_color] if osmc_color in colors_for_way else colors_for_way[consts.hiking_fallback_colour]
            tag_list.add('osmc_order', osmc_order)

            if osmc_foreground in osmc_foregrounds_for_way and osmc_foreground != '':
                tag_list.add('osmc_symbol_order', osmc_foregrounds_for_way[osmc_foreground])

def copy_additional_relation_tags(tag_list_array: [TagListC]):
    pass

## IO Utils and System functions ##

def prefilter_relation_nodes(input_file, output_file):
    """
    Pre-filter tourist routes, nodes junction from the input file and save them to the output file
    :param input_file: input file to read nodes from
    :param output_file: output file to save the nodes
    """

    # check if directory for output file exists and create it if not
    os.makedirs(os.path.dirname(output_file), exist_ok=True)

    # run following command to filter tourist routes and nodes junctions
    system_command = f"osmium tags-filter -o {output_file} {input_file} --overwrite r/route=bicycle,mtb,hiking,foot,ski,piste n/rcn_ref,rwn_ref"

    execute_command(system_command)


# Prepare method to execute command, print potential errors and return output
def execute_command(command):
    process = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = process.communicate()
    if process.returncode != 0:
        logging.error(f"Error during execution of command: {command}")
        logging.error(f"Error message: {err.decode('utf-8')}")
        return None
    return out.decode('utf-8')

