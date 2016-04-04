## AST-X compiler
# Vector multiplication test

a <- 1:1000000
b <- 1:1000000

vectorMult <- function(x, y) {
        return(x * y);
}

vectorMultFastR <- function(x, y) {
	return (x*y);
}


r <- mapply(vectorMultFastR, a, b);

for (i in seq(1,10)) {
        start <- nanotime()
        result <- marawacc.parallelMap(a, vectorMult, b, nThreads=4)
        end <- nanotime()
    	print(identical(r, result))
        print(end-start);
}

print("Sapply")
for (i in seq(1,10)) {
       start <- nanotime()
       r <- mapply(vectorMultFastR, a, b);
       end <- nanotime()
       print(end-start);
}

#print(result) 

