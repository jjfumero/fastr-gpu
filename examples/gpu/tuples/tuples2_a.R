#!/bin/R
a <- 1:1000000

gpuFunction <- function(x) {
	return(list(x * 1, x * 2))
}

for (i in seq(1,10)) {
	result <- marawacc.testGPU(a, gpuFunction)
}
print(result)

