a <- 1:10000
b <- 1:10000

sumArrays <- function(x, y) {
	x * y
}

result <- gpu.parallelMap(a, sumArrays, b)

#print(result) 
