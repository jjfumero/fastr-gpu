a <- 1:1000000
b <- 1:1000000

sumArrays <- function(x) {
	a <- 0;
	for (i in seq(1,1000)) {
		a <- x * 2.0;
	}
	return(a);
}

start <- nanotime()
result <- gpu.parallelMap(a, sumArrays)
end <- nanotime()
print(end-start);

print("Second")
start <- nanotime()
result <- gpu.parallelMap(a, sumArrays)
end <- nanotime()
print(end-start);

print("Three")
start <- nanotime()
result <- gpu.parallelMap(a, sumArrays)
end <- nanotime()
print(end-start);


sumArrays2 <- function(x) {
	a <- 0;
	for (i in seq(1,1000)) {
		a <- x * 2.0;
	}
	return(a);
}

print("Sapply1")
start <- nanotime()
r <- sapply(a, sumArrays2);
end <- nanotime()
print(end-start);


print("Sapply2")
start <- nanotime()
r <- sapply(a, sumArrays2);
end <- nanotime()
print(end-start);

print("Sapply3")
start <- nanotime()
r <- sapply(a, sumArrays2);
end <- nanotime()
print(end-start);

#print(result) 
