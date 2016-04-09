a <- 1:1000000
b <- 1:1000000

f <- function(x) {
        a <- 0;
        for (i in seq(1,1000)) {
       		a <- x * 2.0;
        }
        return(a);
}

g <- function(x) {
        a <- 0;
        for (i in seq(1,1000)) {
            a <- x * 2.0;
        }
        return(a);
}

r <- sapply(a, g);
r1 <- sum(r);

for (i in seq(1,10)) {
        start <- nanotime()
        m1 <- marawacc.map(a, f, nThreads=4)
		s <- marawacc.reduce(m1, function(x, y) x + y);
		result <- marawacc.get(s) 
        end <- nanotime()
    	print(identical(r1, result))
        print(end-start);
}


