#!/bin/R
a <- 1:1000

# Function to execute on the GPU
# f: Integer -> Tuple2<Integer, Integer>
gpuFunction <- function(x) {
	return (list(x, x))
}

result <- marawacc.testGPU(a, gpuFunction)

