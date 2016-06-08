# Author: Juan Fumero 
# Example multithread using sync sapply

a <- 1:10

f <- function(x) {
	return(list(x, x));
}

result <- marawacc.sapply(a, f, nThreads=4)
print(result)
print(result[1])
print(result[10])

