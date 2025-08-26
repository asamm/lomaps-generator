# This is a sample Python script.
import argparse
import enum
import logging
import os

import psutil

import shp2osm.shp2osm
from tourist.tourist2ways import Tourist2Ways
from update.osm_file_validator import OsmFileValidator
from update.osm_update import OsmUpdate


### Custom kill on windows because osmium hangs on the background thttps://github.com/osmcode/pyosmium/discussions/233
def end_program_pids():
    """
    Function to terminate all the python processes that were started by the main process.
    """
    python_pids = get_python_process_pids()
    if python_pids and len(python_pids) > 0:
        for pid in python_pids:
            try:
                print(f"Terminating process with pid {pid}")
                process = psutil.Process(pid)
                process.terminate()
            except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
                pass


def get_python_process_pids():
    pids = psutil.pids()
    python_pids = []
    parent_pid = os.getppid()  # Get the PPID of the current process
    print(parent_pid)
    for pid in pids:
        try:
            process = psutil.Process(pid)
            if 'python' in process.name() and process.ppid() == parent_pid:
                python_pids.append(pid)
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            pass
    return python_pids


class Command(enum.Enum):
    tourist2ways = "tourist2ways"
    osmupdate = "osmupdate"
    shp2osm = "shp2osm"
    # Add more commands here


class ToolsProcessor:

    def __init__(self, option):

        self.options = option

        command_name = self.options.subparser_name
        if command_name == Command.tourist2ways.value:
            print("Process extraction of tourist routes from OSM file")
            tourist2ways = Tourist2Ways(self.options)
        elif command_name == Command.osmupdate.value:
            print("Process update of OSM file")
            osmupdate = OsmUpdate(self.options)
            osmupdate.update(self.options.input)
            # Check if the file is valid after the update
            osm_validator = OsmFileValidator()
            osm_validator.validate(self.options.input)
        elif command_name == Command.shp2osm.value:
            print("Process conversion of shapefile to OSM file")
            shp2osm.shp2osm.convert(self.options)
        else:
            print(f"Command {command_name} is not supported. Supported commands: {Command.tourist2ways.value}")


def parse_options():
    """
    Parse command line parameters

    uses solution https://docs.python.org/dev/library/argparse.html#sub-commands
    """

    parser = argparse.ArgumentParser(
        description='Custom tools used for generation Mapsforge and MVT LoMaps',
        usage='lomaps_tools [<global options>] <command> [<command options>]',
        add_help=True
    )

    parser.add_argument("-v", "--verbose", action="store_true", default=False)

    subparsers = parser.add_subparsers(help='Select tools', dest="subparser_name")

    # Tourist relations to ways
    parser_tourist2way = subparsers.add_parser(Command.tourist2ways.value,
                                               help='Parse OSM file to find Hike, Bike, '
                                                    'and other tourist routes and extract it into separate ways')

    parser_tourist2way.add_argument("-o", "--output", type=str,
                                    help="Path to OSM file where save extracted tourist ways ",
                                    default="output.osm.pbf", required=True)

    parser_tourist2way.add_argument("-i", "--input", type=str,
                                    help="Path to OSM file to parse",
                                    default="input.osm.pbf", required=True)

    parser_tourist2way.add_argument("-ow", "--overwrite", action="store_true",
                                    help="Overwrite output file if exists")

    # add argument for starting way id
    parser_tourist2way.add_argument("-ni", "--nodeid", type=int,
                                    help="Starting custom ID for new points created from tourist relations. "
                                         "Nodes are used for cycling junction in Netherland, Belgium",
                                    default=1200000000000)

    parser_tourist2way.add_argument("-wi", "--wayid", type=int,
                                    help="Starting custom ID new ways created from tourist relations",
                                    default=1300000000000)

    parser_tourist2way.add_argument("-an", "--addwaynodes", action="store_true",
                                    help="Add to output file the original nodes of tourist ways. "
                                         "In normal situation nodes aren't needed because they are "
                                         "included in original planet-file")

    # OSM update
    parser_osmupdate = subparsers.add_parser(Command.osmupdate.value,
                                             help='Update OSM file with new data. Using PyOsmium tool https://github.com/osmcode/pyosmium/blob/master/tools/pyosmium-up-to-date')
    parser_osmupdate.add_argument("-i", "--input", type=str,
                                  help="Path to OSM file to update",
                                  default="input.osm.pbf", required=True)

    # Shapefile to OSM
    parser_shp2osm = subparsers.add_parser(Command.shp2osm.value,
                                           help='Convert shapefile to OSM file')

    parser_shp2osm.add_argument("--id", dest="id", type=int, default=2000000000000,
                                help="ID to start counting from for the output file. Defaults to %(default)s.")

    parser_shp2osm.add_argument("-i", "--input", type=str, help="Path to shapefile to convert",
                                default="input.shp", required=True)

    parser_shp2osm.add_argument("-o", "--output", type=str, help="Path to OSM file where save converted data",
                                default="output.osm.pbf", required=False)

    return parser.parse_args()


def main():
    options = parse_options()

    # Set logging level
    if options.verbose:
        logging.basicConfig(format='%(asctime)s %(message)s', level=logging.INFO)
    else:
        logging.basicConfig(format='%(asctime)s %(message)s', level=logging.INFO)

    ToolsProcessor(options)

    logging.info("Finished processing tools")

    if os.name == 'nt':
        # kill all python processes that were started by the main process
        end_program_pids()

    return 0

if __name__ == '__main__':
    main()