
a <- 1:1000000
b <- 1:1000000

sumArrays <- function(x) {
	a <- 0;
	for (i in seq(1,1000)) {
		a <- x * 2.0;
	}
	return(a);
}

for (i in seq(1,10)) {
	start <- nanotime()
	result <- gpu.parallelMap(a, sumArrays)
	end <- nanotime()
	print(end-start);
}

sumArrays2 <- function(x) {
	a <- 0;
	for (i in seq(1,1000)) {
		a <- x * 2.0;
	}
	return(a);
}

print("Sapply")
for (i in seq(1,10)) {
	start <- nanotime()
	r <- sapply(a, sumArrays2);
	end <- nanotime()
	print(end-start);
}

#print(result) 
