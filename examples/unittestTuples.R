
runTests <- function() {
	# List of tests
    tests <- c( 
				"testTuple2Div.R",
				"testTuple2.R",
				"testTuple2Sub.R",
				"testTuple2Sum.R",
				"testTuple3op.R",
				"testTuple3.R"
			  )

    for (t in tests) {
		print("\n================================\n")
        cat(paste("Running ",t, "\n"))
        source(paste0("./examples/gpu/tuples/", t))
    }
}

runTests()

