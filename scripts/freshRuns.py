"""
Author: Juan Fumero
Email : juan.fumero@ed.ac.uk
"""

import os

SCRIPT="./runbench "
URL="benchmarks/fastR/"


DEBUG = True

def execute(bench, sizes, version, symName):
    print "\nRunning: " + version
    logFile = symName 
    for s in sizes:
        command = SCRIPT + URL + bench + "/" + version + " " + str(s) + " > " + logFile + str(s) + ".log"
        print command
        if (not DEBUG):
            os.system(command)

def saxpy():
    # Saxpy 8M
    bench="saxpy"
    sizes = [2097152, 4194304, 8388608, 16777216, 33554432]

    versions = ["saxpy.R", "saxpySeq.R", "saxpyGPU.R", "saxpyGPUPArrays"]
    symbolicNames = ["saxpyFastR", "saxpySeq", "saxpyASTxFull", "saxpyASTx"]

    for i in range(len(versions)):
        version = versions[i]
        symbolicName = symbolicNames[i]
        execute(bench, sizes, version, symbolicName)


if __name__ == "__main__":
    
    saxpy()
