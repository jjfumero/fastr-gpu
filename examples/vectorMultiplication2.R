## AST-X compiler
# Vector multiplication test

a <- 1:1000000
b <- 1:1000000
c <- 1:1000000

vectorMult <- function(x, y, z) {
        return(x * y * z);
}

vectorMultFastR <- function(x, y, z) {
	return (x * y * z);
}


r <- mapply(vectorMultFastR, a, b, c);

for (i in seq(1,10)) {
        start <- nanotime()
        result <- marawacc.parallelMap(a, vectorMult, b, c, nThreads=4)
        end <- nanotime()
    	print(identical(r, result))
        print(end-start);
}

print("Sapply")
for (i in seq(1,10)) {
       start <- nanotime()
       r <- mapply(vectorMultFastR, a, b, c);
       end <- nanotime()
       print(end-start);
}

#print(result) 

