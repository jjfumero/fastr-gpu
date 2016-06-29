#!/bin/R
a <- 1:1000000

#tt2 <- function(x, y) {
#    t <- list(name="t2", a=x, b=y)  
#    attr(t, "class") <- "t2"
#    return(t)
#}

tt2 <- function(x,y) {
	list(x,y)
}

# Function to execute on the GPU
# f: Integer -> Tuple2<Integer, Integer>
gpuFunction <- function(x) {
	return(tuple2(x, x))
	#return(list(x,x))
}

for (i in seq(1,10)) {
	result <- marawacc.testGPU(a, gpuFunction)
}
#print(result)

