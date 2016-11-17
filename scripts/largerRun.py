#!/usr/bin/python

"""
Author: Juan Fumero
Email : juan.fumero@ed.ac.uk
"""

# ########################################
# Configuraion variables
# ########################################
SCRIPT="./runbench "
URL="benchmarks/fastR/"
DIRNAME = "veeColdRUN"
NUMBER_OF_FRESH_RUNS=3
DEBUG = True
# ########################################

import os

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
    sizes = []
    divide = 1/8.0
    #divide = 128
    value = int(mainSize/divide)
    for i in range(2):
        sizes.append(value)
        value *= 2

    #versions = ["saxpySeq.R", "saxpyGPU.R", "saxpyGPUPArrays.R"]
    versions = ["saxpySeq.R"]
    #symbolicNames = ["saxpySeq", "saxpyASTxFull", "saxpyASTx"]
    symbolicNames = ["saxpySeq"]

    runExperiment(bench, sizes, versions, symbolicNames)

def blacksholes():
    bench = "blackscholes"
    
    mainSize = 1048576
    sizes = []
    divide = 128
    divide = 1/8.0
    value = int(mainSize/divide)
    for i in range(4):
        sizes.append(value)
        value *= 2

    #versions = ["blackscholesSeq.R",  "blackscholesGPU.R", "blackscholesGPUPArrays.R"]
    versions = ["blackscholesSeq.R"]
    #symbolicNames = ["blackcholesSeq", "blackcholesASTxFull", "blackcholesASTx"]
    symbolicNames = ["blackcholesSeq"]
    
    runExperiment(bench, sizes, versions, symbolicNames)

def nbody():
    bench = "nbody"

    mainSize = 65536
    sizes = []
    divide = 128
    value = mainSize/divide
    for i in range(10):
        sizes.append(value)
        value *= 2

    #versions = ["nbodySeq.R", "nbodyGPU.R", "nbodyGPUPArrays.R"]
    versions = ["nbodyGPU.R"]
    #symbolicNames = ["nbodySeq", "nbodyASTxFull", "nbodyASTx"]
    symbolicNames = ["nbodyASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)

def dft():
    bench = "dft"

    mainSize = 8192
    sizes = []
    divide = 128
    value = mainSize/divide
    for i in range(10):
        sizes.append(value)
        value *= 2

    #versions = ["dftSeq.R",  "dftGPU.R", "dftGPUPArrays.R"]
    versions = ["dftGPU.R"]
    #symbolicNames = ["dftSeq", "dftASTxFull", "dftASTx"]
    symbolicNames = ["dftASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)
 
def mandelbrot():
    bench = "mandelbrot"
    mainSize = 1024
    sizes = []
    #divide = 128
    divide = 1/8.0
    value = int(mainSize/divide)
    for i in range(4):
        sizes.append(value)
        value *= 2

    #versions = ["mandelbrotSeq.R",  "mandelbrotGPU.R", "mandelbrotGPUPArrays.R"]
    versions = ["mandelbrotSeq.R"]
    #symbolicNames = ["mandelbrotSeq", "mandelbrotASTxFull", "mandelbrotASTx"]
    symbolicNames = ["mandelbrotSeq"]
    
    runExperiment(bench, sizes, versions, symbolicNames)

def kmeans():
    bench = "kmeans"
    mainSize = 4194304
    sizes = []
    #divide = 256
    divide = 1/4.0
    value = int(mainSize/divide)
    for i in range(4):
        sizes.append(value)
        value *= 2

    #versions = ["kmeansSeq.R", "kmeansGPU.R", "kmeansGPUPArrays.R"]
    versions = ["kmeansSeq.R"]
    #symbolicNames = ["kmeansSeq", "kmeansASTxFull", "kmeansASTx"]
    symbolicNames = ["kmeansSeq"]
    
    runExperiment(bench, sizes, versions, symbolicNames)
 
def hilbert():
    bench = "hilbert"
    mainSize = 4096
    sizes = []
    #divide = 256
    divide = 1/4.0
    value = int(mainSize/divide)
    for i in range(4):
        sizes.append(value)
        value *= 2

    #versions = ["hilbertSeq.R", "hilbertGPU.R", "hilbertGPUPArrays.R"]
    versions = ["hilbertGPU.R"]
    #symbolicNames = ["hilbertSeq", "hilbertASTxFull", "hilbertASTx"]
    symbolicNames = ["hilbertASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)
 
def spectralNorm():
    bench = "spectralNorm"
    mainSize = 32768
    sizes = []
    divide = 128
    value = mainSize/divide
    for i in range(10):
        sizes.append(value)
        value *= 2

    #versions = ["spectralNormSeq.R", "spectralNormGPU.R", "spectralNormGPUPArrays.R"]
    versions = ["spectralNormGPU.R"]
    #symbolicNames = ["spectralNormSeq",  "spectralNormASTxFull", "spectralNormASTx"]
    symbolicNames = ["spectralNormASTx"]
    
    runExperiment(bench, sizes, versions, symbolicNames)

def runBenchmarks():
    saxpy()
    blacksholes()   
    #nbody()
    #dft()
    mandelbrot()
    kmeans()
    hilbert()
    #spectralNorm()

# ###############################
if __name__ == "__main__":
    createDirectory(DIRNAME)
    runBenchmarks()    

