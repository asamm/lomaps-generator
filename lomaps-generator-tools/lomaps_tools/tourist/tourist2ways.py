import logging
import os

from osm.osm_writer import OsmWriter
from tourist.handlers import TouristRouteHandler, WayHandler, WayNodeHandler
from tourist.helpers import prefilter_relation_nodes
from tourist.storage import DataStorage
from utils.cmd_utils import is_tool_installed


class Tourist2Ways:

    def __init__(self, options):
        self.options = options
        self.data_storage = DataStorage()

        self.checkFiles(options)

        # check osmium is installed in the system and set flag to true if installed
        is_omium_installed = is_tool_installed('osmium')

        input_file = options.input

        if is_omium_installed:
            logging.info("Osmium is installed in the system - pre-filter data first")
            logging.info("Pre-Filter tourist routes and ways")
            input_file = "_tmp/filtered.osm"
            prefilter_relation_nodes(options.input, input_file)

        # Process relations to find all tourist relations
        logging.info("Step 1. Filter tourist relations")
        route_handler = TouristRouteHandler(self.data_storage)
        route_handler.apply_file(input_file)
        route_handler.post_process()

        # Step 2: Process ways to extract ways that are members of the hiking routes
        logging.info("Step 2. Extract ways of tourist relations")
        # Init writer
        osm_writer = OsmWriter(self.options.output)

        # # Update network nodes
        # logging.info("Update network nodes")
        # network_node_updater = NetworkNodeUpdater(self.data_storage, osm_writer)
        # network_node_updater.write_node_junctions()

        # second iteration of osm file to process ways those are members of tourist relations
        logging.info("Process tourist ways")
        way_handler = WayHandler(options, self.data_storage, osm_writer)
        way_handler.apply_file(input_file)

        if options.addwaynodes:
            logging.info("Read input file, find nodes those are part of tourist ways and add them to the output file")
            way_node_handler = WayNodeHandler(self.data_storage, osm_writer)
            way_node_handler.apply_file(input_file)

        # Close the writer
        osm_writer.close_writer()

        # Output the extracted ways
        if is_omium_installed:
            logging.info("Delete tmp files")
            os.remove(input_file)

    def checkFiles(self, options):
        """
        Check if the input file exists and the output file does not exist (if overwrite option is not set)
        :param options: command line options
        """

        if not os.path.exists(options.input):
            raise FileNotFoundError(f"File {options.input} not found")
        if os.path.exists(options.output) and not options.overwrite:
            raise FileExistsError(f"File {options.output} exists. Use -ow option to overwrite")
