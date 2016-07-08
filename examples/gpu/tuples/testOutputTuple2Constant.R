#!/bin/R
a <- 1:1000000

# Function to execute on the GPU
# f: Integer -> Tuple2<Integer, Integer>
gpuFunction <- function(x) {
	return(list(x,0))
}

for (i in seq(1,10)) {
	result <- marawacc.testGPU(a, gpuFunction)
}
#print(result)

