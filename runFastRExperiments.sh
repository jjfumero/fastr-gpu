#!/bin/bash

NOW=""

function getDate() {
	NOW=$(date +"%m-%d-%Y-%H-%M-%S")
}

function runSaxpy {
	logFile=$1
    for size in 100 200 8388608 16777216 33554432 67108864 134217728
    do
        echo "Running saxpy with : $size" 
        ./runbench benchmarks/fastR/saxpy/$2 $size >> $logFile
    done
}

function runBS {
    # 1048576:67108864:*2
	logFile=$1
    for size in 1000 2000 1048576 2097152 4194304 8388608 16777216 33554432 67108864
    do
        echo "Running blackscholes with : $size" 
        ./runbench benchmarks/fastR/blackscholes/$2 $size >> $logFile
    done
}

function runMC {
	logFile=$1
    #262144:2097152;*2
    for size in 262144 524288 1048576 2097152
    do
        echo "Running montecarlo with : $size" 
        ./runbench benchmarks/fastR/montecarlo/$2 $size >> $logFile
    done
}

function runKmeans {
	logFile=$1
    for size in 4194304 8388608 16777216 33554432  
    do
        echo "Running kmeans with : $size" 
        ./runbench benchmarks/fastR/kmeans/$2 $size >> $logFile
    done
}

function runNBody {
	logFile=$1
    for size in 65536 131072 262144 524288 
    do
        echo "Running nbody with : $size" 
        ./runbench benchmarks/fastR/nbody/$2 $size >> $logFile
    done
}

function runDFT {
	logFile=$1
    for size in 4194304 8388608 16777216 33554432 
    do
        echo "Running dft with : $size" 
        ./runbench benchmarks/fastR/dft/$2 $size >> $logFile
    done
}

function runSpectralNorm {
	logFile=$1
    for size in 4194304 8388608 16777216 33554432 
    do
        echo "Running spectralNorm with : $size" 
        ./runbench benchmarks/fastR/spectralNorm/$2 $size >> $logFile
    done
}

function runEuler {
	logFile=$1
    for size in 32 64 128 256 512 
    do
        echo "Running euler with : $size" 
        ./runbench benchmarks/fastR/euler/$2 $size >> $logFile
    done
}

function runMandelbrot {
	logFile=$1
    for size in 262144 524288 1048576 2097152
    do
        echo "Running mandelbrot with : $size" 
        ./runbench benchmarks/fastR/mandelbrot/$2 $size >> $logFile
    done
}

function runCPU() {
	echo " ====================================" 
	echo "CPU"
	echo "SAXPY"
	getDate
	logFile="fastr_saxpyLogs_$NOW.log"	
	runSaxpy $logFile saxpy.R

	echo "BS"
    	getDate
    	logFile="fastr_blackscholesLogs_$NOW.log"
	runBS $logFile blackscholes.R

	echo "MonteCarlo"
    	getDate
    	logFile="fastr_montecarloLogs_$NOW.log"
	runMC $logFile montecarlo.R

	echo "KMeans"
    	getDate
    	logFile="fastr_kmemansLogs_$NOW.log"	
	runKmeans $logFile kmeans.R

	echo "NBody"
        getDate
        logFile="fastr_nbodyLogs_$NOW.log"	
	runNBody $logFile nbody.R

	echo "DFT"
	getDate
    	logFile="fastr_dftLogs_$NOW.log"	
	runDFT $logFile dft.R

	echo "SpectralNorm"
	getDate
    	logFile="fastr_spectralNormLogs_$NOW.log"	
	runSpectralNorm $logFile spectralNorm.R

	echo "Euler"
	getDate
    	logFile="fastr_eulerLogs_$NOW.log"	
	runEuler $logFile euler.R

	echo "Mandelbrot"
	getDate
    	logFile="fastr_mandelbrotLogs_$NOW.log"	
	runMandelbrot $logFile mandelbrot.R
}

function runGPU() {
	echo " ====================================" 
	echo "GPU - OpenCL"
	echo "SAXPY"
	getDate
    	logFile="astx_saxpyLogs_$NOW.log"
	runSaxpy $logFile saxpyGPU.R

	echo "BS"
	    getDate
    	logFile="astx_blackscholesLogs_$NOW.log"
	runBS $logFile blackscholesGPU.R

	echo "MonteCarlo"
	    getDate
    	logFile="astx_montecarloLogs_$NOW.log"
	runMC $logFile montecarloGPU.R
	
	
	echo "KMEANS"
	getDate
	logFile="astx_kmeansLogs_$NOW.log"
	runKmeans $logFile kmeansGPU.R
	
	echo "NBODY"
	getDate
	logFile="astx_nbodyLogs_$NOW.log"
	runNBody $logFile nbodyGPU.R
	
	echo "DFT"
	getDate
	logFile="astx_dftLogs_$NOW.log"
	runDFT $logFile dftGPU.R

	echo "SpectralNorm"
	getDate
	logFile="astx_spectralNormLogs_$NOW.log"
	runSpectralNorm $logFile spectralNormGPU.R

	echo "Mandelbrot"
	getDate
	logFile="astx_mandelbrotLogs_$NOW.log"
	runMandelbrot $logFile mandelbrotGPU.R
}

# ###############################################
# Run experiments
# ###############################################
runCPU
runGPU
