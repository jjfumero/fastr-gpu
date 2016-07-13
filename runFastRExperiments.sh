#!/bin/bash

NOW=""

function getDate() {
	NOW=$(date +"%m-%d-%Y-%H-%M-%S")
}


function runSaxpy {
	logFile=$1
    for size in 8388608 16777216 33554432 67108864 134217728
    do
        echo "Running saxpy with : $size" 
        ./run benchmarks/fastR/saxpy/$2 $size > ../$logFile
    done
}

function saxpy() {
	echo "CPU"
    getDate
    logFile="fastr_saxpyLogs_$NOW.log"	
	runSaxpy $logFile saxpy.R
	
	echo "GPU"
	getDate
    logFile="astx_saxpyLogs_$NOW.log"
	runSaxpy $logFile saxpyGPU.R
}


function runBS {
    # 1048576:67108864:*2
	logFile=$1
    for size in 1048576 2097152 4194304 8388608 16777216 33554432 67108864
    do
        echo "Running blackscholes with : $size" 
        ./run benchmarks/fastR/blackscholes/$2 $size > ../$logFile
    done


}

function blackscholes() {
	echo "FastR"
    getDate
    logFile="fastr_blackscholesLogs_$NOW.log"
	runBS $logFile blackscholes.R

	echo "GPU"
    getDate
    logFile="astx_blackscholesLogs_$NOW.log"
	runBS $logFile blackscholesGPU.R
}


# main
saxpy
blackscholes
