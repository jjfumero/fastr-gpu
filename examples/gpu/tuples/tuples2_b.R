#!/bin/R
a <- 1:1000000

gpuFunction <- function(x) {
	a <- x * x
	for (i in 1:10000) {
		a <- a + i
	}
	return(list(x, a))
}

for (i in seq(1,10)) {
	result <- marawacc.testGPU(a, gpuFunction)
}
print(result)

