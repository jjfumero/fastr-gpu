"""
Author: Juan Fumero
Email : juan.fumero@ed.ac.uk
"""

import os

SCRIPT="./runbench "
URL="benchmarks/fastR/"
DIRNAME = "logsSizes"
DEBUG = False

NUMBER_OF_FRESH_RUNS=1

def createDirectory(directoryName):
    if not os.path.exists(directoryName):
        os.makedirs(directoryName)

def execute(bench, sizes, version, symName):
    print "\nRunning: " + version
    logFile = DIRNAME + "/" + symName 
    for s in sizes:
        for i in range(NUMBER_OF_FRESH_RUNS):
            command = SCRIPT + URL + bench + "/" + version + " " + str(s) + " > " + logFile + str(s) + ".log." + str(i)
            print "\t" + command
            if (not DEBUG):
                os.system(command)

def runExperiment(bench, sizes, versions, symbolicNames):
    for i in range(len(versions)):
        version = versions[i]
        symbolicName = symbolicNames[i]
        execute(bench, sizes, version, symbolicName)

def saxpy():
    bench="saxpy"

    mainSize = 8388608
    sizes = [mainSize/4, mainSize/2, mainSize, mainSize*2, mainSize*4]

    versions = ["saxpyGPU.R", "saxpyGPUPArrays.R"]
    symbolicNames = ["saxpyASTxFull", "saxpyASTx"]

    runExperiment(bench, sizes, versions, symbolicNames)

def blacksholes():
    bench = "blackscholes"
    
    mainSize = 1048576
    sizes = [mainSize/4, mainSize/2, mainSize, mainSize*2, mainSize*4]

    versions = ["blackscholesGPU.R", "blackscholesGPUPArrays.R"]
    symbolicNames = ["blackcholesASTxFull", "blackcholesASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)

def nbody():
    bench = "nbody"

    mainSize = 65536
    sizes = [mainSize/4, mainSize/2, mainSize, mainSize*2, mainSize*4]

    versions = ["nbodyGPU.R", "nbodyGPUPArrays.R"]
    symbolicNames = ["nbodyASTxFull", "nbodyASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)

def dft():
    bench = "dft"

    mainSize = 8192
    sizes = [mainSize/4, mainSize/2, mainSize, mainSize*2, mainSize*4]

    versions = ["dftGPU.R", "dftGPUPArrays.R"]
    symbolicNames = ["dftASTxFull", "dftASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)
 
def mandelbrot():
    bench = "mandelbrot"
    mainSize = 1024
    sizes = [mainSize/4, mainSize/2, mainSize, mainSize*2, mainSize*4]

    versions = [ "mandelbrotGPU.R", "mandelbrotGPUPArrays.R"]
    symbolicNames = ["mandelbrotASTxFull", "mandelbrotASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)

def kmeans():
    bench = "kmeans"
    mainSize = 4194304
    sizes = [mainSize/4, mainSize/2, mainSize, mainSize*2, mainSize*4]

    versions = ["kmeansGPU.R", "kmeansGPUPArrays.R"]
    symbolicNames = ["kmeansASTxFull", "kmeansASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)
 
def hilbert():
    bench = "hilbert"
    mainSize = 4096
    sizes = [mainSize/4, mainSize/2, mainSize, mainSize*2, mainSize*4]

    versions = ["hilbertGPU.R", "hilbertGPUPArrays.R"]
    symbolicNames = ["hilbertASTxFull", "hilbertASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)
 
def spectralNorm():
    bench = "spectralNorm"
    mainSize = 32768
    sizes = [mainSize/4, mainSize/2, mainSize, mainSize*2, mainSize*4]

    versions = ["spectralNormGPU.R", "spectralNormGPUPArrays.R"]
    symbolicNames = ["spectralNormASTxFull", "spectralNormASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)
 
if __name__ == "__main__":

    createDirectory(DIRNAME)
    
    saxpy()
    blacksholes()   
    nbody()
    dft()
    mandelbrot()
    kmeans()
    hilbert()
    spectralNorm()

