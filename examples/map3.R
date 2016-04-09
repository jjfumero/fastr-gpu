a <- 1:1000000
b <- 1:1000000

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

f3 <- function(x) {
	return(x + x)
}

g <- function(x) {
        a <- 0;
        for (i in seq(1,1000)) {
            a <- x * 2.0;
        }
        return(a);
}

r1 <- sapply(a, g);
r2 <- sapply(r1, f2);
r3 <- sapply(r2, f3);

for (i in seq(1,10)) {
        start <- nanotime()
        m1 <- marawacc.map(a, f, nThreads=4)
		m2 <- marawacc.map(m1, f2, nThreads=2);
		m3 <- marawacc.map(m2, f3, nThreads=8);
		result <- marawacc.execute(m3) 
        end <- nanotime()
    	print(identical(r3, result))
        print(end-start);
}


