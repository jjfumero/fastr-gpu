
f <- function(x) x + 100
a <- 10
a[1000] <- 20
marawacc.init()
mapply(f, a)

