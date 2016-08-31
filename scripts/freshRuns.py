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

def runExperiment(bench, sizes, versions, symbolicNames):
    for i in range(len(versions)):
        version = versions[i]
        symbolicName = symbolicNames[i]
        execute(bench, sizes, version, symbolicName)


def saxpy():
    bench="saxpy"
    sizes = [2097152, 4194304, 8388608, 16777216, 33554432]

    versions = ["saxpy.R", "saxpySeq.R", "saxpyGPU.R", "saxpyGPUPArrays"]
    symbolicNames = ["saxpyFastR", "saxpySeq", "saxpyASTxFull", "saxpyASTx"]

    runExperiment(bench, sizes, versions, symbolicNames)

def blacksholes():
    bench = "blacksholes"
    sizes = [1, 2, 3, 4, 5]

    versions = ["blackcholes.R", "blackcholesSeq.R", "blackcholesGPU.R", "blackcholesGPUPArrays"]
    symbolicNames = ["blackcholesFastR", "blackcholesSeq", "blackcholesASTxFull", "blackcholesASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)
   
if __name__ == "__main__":
    
    saxpy()
    blacksholes()


