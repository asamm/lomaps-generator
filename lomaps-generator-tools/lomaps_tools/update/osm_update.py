import subprocess

from update.osm_file_validator import OsmFileValidator
from utils.cmd_utils import get_pyosmium_up_to_date_path


class OsmUpdate:
    def __init__(self, options):
        self.options = options

    def update(self, osm_file):
        # run  pyosmium-up-to-date to update the osm file
        # if program returns 0 updates have been successfully applied up to the newest data or no new data was available.
        # It returns 1, repeat the process

        pyosmium_up_to_date_path = get_pyosmium_up_to_date_path()
        command = pyosmium_up_to_date_path.split() + [osm_file , '-v']
        print (command)
        loop_counter = 0
        while loop_counter < 1000:
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

            # Print each line of stdout as it becomes available
            for line in process.stdout:
                print(line, end='')  # Print each line as it arrives without extra newline

            # Print each line of stderr as it becomes available
            for line in process.stderr:
                print(line, end='')  # Handle error messages if needed

            # Wait for the process to complete and get the return code
            process.wait()
            if process.returncode == 0:
                print("Updates have been successfully applied up to the newest data or no new data was available.")
                break
            elif process.returncode == 1:
                print("New data available, repeating the process.")
                loop_counter += 1
                continue
            else:
                raise Exception(f"Error: pyosmium-up-to-date returned code {process.returncode}.")
        if loop_counter >= 1000:
            print("Reached maximum number of loops (1000) without completing the update.")

