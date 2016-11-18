
## ASTx
## NBody benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./nbodyGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 1

NUMBODIES <- size
DELTA <- 0.005
ESPSQRT <- 500

CHECK <- FALSE

nbodyCPU <- function(px, py, pz, vx, vy, vz) {
	acc1 <- 0
	acc2 <- 0
	acc3 <- 0
		
	for (i in 1:NUMBODIES) {
		body <- 4*i
		distSrt <- 0.0

		r1 <- positions[body] - px	
		r2 <- positions[body + 1] - py
		r3 <- positions[body + 2] - pz

		distSrt <- distSrt + (r1 * r1)
		distSrt <- distSrt + (r2 * r2)
		distSrt <- distSrt + (r3 * r3)

		invDist <- 1.0 / sqrt(distSrt + ESPSQRT)
		invDistCube <- invDist * invDist * invDist;	
		s <- positions[body + 3] * invDistCube;

        acc1 <- acc1 + (s * r1);
        acc2 <- acc2 + (s * r2);
        acc3 <- acc3 + (s * r3);
    }

	npx <- px + (vx * DELTA + 0.5 * acc1 * DELTA * DELTA);
	npy <- py + (vy * DELTA + 0.5 * acc2 * DELTA * DELTA);
	npz <- pz + (vz * DELTA + 0.5 * acc3 * DELTA * DELTA);

  	nvx <- vx + ( acc1 * DELTA);
 	nvy <- vy + ( acc2 * DELTA);
  	nvz <- vz + ( acc3 * DELTA);
	
	result <- list(npx, npy, npz, nvx, nvy, nvz)
	return(result)	
}


nbodyFunction <- function(px, py, pz, vx, vy, vz) {
	acc1 <- 0
	acc2 <- 0
	acc3 <- 0
		
	for (i in 1:NUMBODIES) {
		body <- 4*i
		distSrt <- 0.0

		r1 <- positions[body] - px	
		r2 <- positions[body + 1] - py
		r3 <- positions[body + 2] - pz

		distSrt <- distSrt + (r1 * r1)
		distSrt <- distSrt + (r2 * r2)
		distSrt <- distSrt + (r3 * r3)

		invDist <- 1.0 / sqrt(distSrt + ESPSQRT)
		invDistCube <- invDist * invDist * invDist;	
		s <- positions[body + 3] * invDistCube;

        acc1 <- acc1 + (s * r1);
        acc2 <- acc2 + (s * r2);
        acc3 <- acc3 + (s * r3);
    }

	npx <- px + (vx * DELTA + 0.5 * acc1 * DELTA * DELTA);
	npy <- py + (vy * DELTA + 0.5 * acc2 * DELTA * DELTA);
	npz <- pz + (vz * DELTA + 0.5 * acc3 * DELTA * DELTA);

  	nvx <- vx + ( acc1 * DELTA);
 	nvy <- vy + ( acc2 * DELTA);
  	nvz <- vz + ( acc3 * DELTA);
	
	result <- list(npx, npy, npz, nvx, nvy, nvz)
	return(result)	
}


benchmark <- function(inputSize) {
	px <- runif(size)
	py <- runif(size)
	pz <- runif(size)
	vx <- runif(size)
	vy <- runif(size)
	vz <- runif(size)

	positions <<- 1:size*4

	for (i in 1:size) {
		idx <- i * 4	
		positions[idx] <<- px[i]
		positions[idx+1] <<- py[i]
		positions[idx+2] <<- pz[i]
		positions[idx+3] <<- runif(1) # mass
	}

	seq <- mapply(nbodyCPU, px, py, pz, vx, vy, vz)

	for (i in 1:REPETITIONS) {
		system.gc()
		start <- nanotime()
		result <- marawacc.gpusapply(px, nbodyFunction, py, pz, vx, vy, vz);
		end <- nanotime()
		total <- end - start
		print(total)

		if(CHECK) {
			k <- 1
			j <- 1
			while (k < size) {
			    lseq <- c(seq[[k]], seq[[k+1]], seq[[k+2]], seq[[k+3]], seq[[k+4]], seq[[k+5]])
			    lgpu <- c(result[[k]], result[[k+1]], result[[k+2]], result[[k+3]], result[[k+4]], result[[k+5]])
    			#lgpu <- c(result[[j]][[1]], result[[j]][[2]], result[[j]][[3]], result[[j]][[4]], result[[j]][[5]], result[[j]][[6]])
	
				for (ii in 1:6) {
		    		# check the elements for the tuple
		    		if (abs(lseq[ii] - lgpu[ii]) > LIMIT) {
    			    	print("Result is wrong")
				        break;
				    }
				}

		    	k <- k + 6
			    j <- j + 1
			}
		}
	}
}

LIMIT <- 0.5

## Main
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

