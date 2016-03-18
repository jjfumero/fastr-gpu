a <- 1:1000000
b <- 1:1000000

sumArrays <- function(x) {
	a <- 0;
	for (i in seq(1,1000)) {
		a <- x * 2.0;
	}
	return(a);
}

result <- gpu.parallelMap(a, sumArrays)

print("Second")
result <- gpu.parallelMap(a, sumArrays)

#print(result) 
