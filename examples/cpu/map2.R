# Author: Juan Fumero
# Example of contatenation of two map asynchronous operations

a <- 1:1000000

f <- function(x) {
        a <- 0;
        for (i in seq(1,1000)) {
       		a <- x * 2.0;
        }
        return(a);
}

f2 <- function(x) {
	return(x * x)
}

g <- function(x) {
        a <- 0;
        for (i in seq(1,1000)) {
            a <- x * 2.0;
        }
        return(a);
}

r <- sapply(a, g);
r2 <- sapply(r, f2);



for (i in seq(1,10)) {
        start <- nanotime()
        m1 <- marawacc.map(a, f, nThreads=4)
		m2 <- marawacc.map(m1, f2);
		result <- marawacc.get(m2) 
        end <- nanotime()
    	print(identical(r2, result))
        print(end-start);
}


print("Sapply")
for (i in seq(1,10)) {
       start <- nanotime()
       r <- sapply(a, g);
       end <- nanotime()
       print(end-start);
}

#print(result) 

