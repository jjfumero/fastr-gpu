a <- 1:1000000
b <- 1:1000000

sumArrays <- function(x) {
	x * 2.0
}

result <- gpu.parallelMap(a, sumArrays)

#print(result) 
