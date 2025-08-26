import subprocess

from utils.cmd_utils import get_osmium_path


class OsmFileValidator:

    def __init__(self):
        pass


    def validate(self, osm_file_path: str) -> bool:
        """
        Validates the OSM file using 'osmium fileinfo'.
        Raise exception if file isn't valid
        """
        print("Validating OSM file:", osm_file_path)
        command = [get_osmium_path(), "fileinfo", "--extended", osm_file_path]
        try:
            result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            if result.returncode == 0:
                return True
            else:
                print(f"osmium fileinfo error: {result.stderr}")
                # If the command fails, it means the file is not valid > raise an exception
                raise Exception(f"OSM file {osm_file_path} is not valid: {result.stderr.strip()}")

        except Exception as e:
            print(f"Failed to run osmium fileinfo: {e}")
            return False

