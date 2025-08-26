
# the list of values of "state" tag that represents not active tourist route (such route is not added into tourist ways)
invalid_states_for_tourist_route = ["proposed", "disused", "removed", "abandoned"]

# the list of values of "type" tag that represents not active tourist route (such route is not added into tourist ways)
invalid_types_for_tourist_route = ["network"]

# order of bicycle network tag
bycicle_network_type = {
    "lcn": 1,
    "rcn": 2,
    "ncn": 3,
    "icn": 4
}

# order of hiking network tag
hiking_network_type = {
    "lwn": 1,
    "rwn": 2,
    "nwn": 3,
    "iwn": 4
}

# ist of possible colors of hiking routes
hiking_colour_type = [
    "black",
    "blue",
    "brown",
    "green",
    "orange",
    "purple",
    "red",
    "white",
    "yellow"
]

hiking_fallback_colour = "red"  # fallback color for hiking routes if no color is specified

# suported Network node (Junctions in NL, BE) keys
network_node_keys = ['rcn_ref', 'rwn_ref']

# LIST of keys that are important for the tourist route, other routes tags are ignored, these tag keys are also copied to the child ways
tourist_route_cached_keys = ['network', 'route', 'name', 'ref', 'osmc:symbol', 'osmc', 'piste:type', 'piste:grooming', 'piste:difficulty', 'type', 'educational']

key_inherited_from_orig_way_keys = ['bridge', 'highway', 'layer', 'mtb:scale', 'sac_scale', 'tunnel']