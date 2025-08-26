import logging
import os
import shutil
import subprocess
import sys


def get_pyosmium_up_to_date_path():
    """
    Find the path to the pyosmium-up-to-date command.
    :return: path to the pyosmium-up-to-date command or raises FileNotFoundError if not found.
    """
    # check if pyosmium-up-to-date is installed in the system or avaiable in the PATH pyosmium-up-to-date
    paths = ['pyosmium-up-to-date',
             os.path.join(os.path.dirname(sys.executable), 'pyosmium-up-to-date'),
             sys.executable + ' ' + os.path.join(os.path.dirname(sys.executable),'Scripts', 'pyosmium-up-to-date')]

    pyosmium_up_to_date_path = find_runnable_command(paths)
    if pyosmium_up_to_date_path is None:
        raise FileNotFoundError("pyosmium-up-to-date not found in the system. Please install it.")

    return pyosmium_up_to_date_path

def get_osmium_path():
    """
    Find the path to the osmium command.
    :return: path to the osmium command or raises FileNotFoundError if not found.
    """
    # check if osmium is installed in the system or avaiable in the PATH
    paths = ['osmium', sys.executable + ' ' + os.path.join(os.path.dirname(sys.executable), 'osmium')]

    osmium_path = find_runnable_command(paths)
    if osmium_path is None:
        raise FileNotFoundError("osmium not found in the system. Please install it.")

    return osmium_path

def is_tool_installed(name):
    """Check whether `name` is on PATH."""
    if shutil.which(name) is None:
        logging.info(f"Tool {name} is not installed in the system. Please install it and add it to PATH.")
        return False

    return True


def find_runnable_command(commands):
    """
    Find the first command in the list that can be run
    :param commands: commands, paths to check
    :return:
    """

    for command in commands:
        try:
            # Try to run the command with `--version` or any harmless argument
            # Log the command being tested
            logging.debug(f"Testing command: {command}")
            result = subprocess.run(command.split() + ['--version'],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            # If the command runs successfully, return the command name or path
            if result.returncode == 0:
                return command
        except FileNotFoundError:
            # Command not found, move to the next one in the list

            continue
    # If no command is found, return None
    return None


