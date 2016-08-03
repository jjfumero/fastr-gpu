
## ASTx
## NBody benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./nbody.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 10

NUMBODIES <- size
DELTA <- 0.005
ESPSQRT <- 500

## Lambda expression for the computation
benchmark <- function(inputSize) {

	nbodyFunction <- function(px, py, pz, vx, vy, vz) {
		acc <- c(0.0, 0.0, 0.0)
		for (i in 1:NUMBODIES) {
			body <- 4*i
			r <- c(0.0,0.0,0.0)
			distSrt <- 0.0
			
			r[1] <- positions[body] - px	
			r[2] <- positions[body + 1] - py
			r[3] <- positions[body + 2] - pz	

			for (j in 1:3) {
				distSrt <- distSrt + (r[j] * r[j])
			}
	
			invDist <- 1.0 / sqrt(distSrt + ESPSQRT)
			invDistCube <- invDist * invDist * invDist;	
			s <- positions[body + 3] * invDistCube;

			for (k in 1:3) {
             	acc[k] <- acc[k]  + (s * r[k]);
            }
		}

		npx <- px + (vx * DELTA + 0.5 * acc[1] * DELTA * DELTA);
		npy <- py + (vy * DELTA + 0.5 * acc[2] * DELTA * DELTA);
		npz <- pz + (vz * DELTA + 0.5 * acc[3] * DELTA * DELTA);

      	nvx <- vx + ( acc[1] * DELTA);
     	nvy <- vy + ( acc[2] * DELTA);
      	nvz <- vz + ( acc[3] * DELTA);
		
		result <- list(px, py, pz, vx, vy, vz)
		return(result)	
	}

	px <- runif(size)
	py <- runif(size)
	pz <- runif(size)
	vx <- runif(size)
	vy <- runif(size)
	vz <- runif(size)

	positions <- 1:size*4
	
	for (i in 1:size) {
		idx <- i * 4	
		positions[idx] <- px[i]
		positions[idx+1] <- py[i]
		positions[idx+2] <- pz[i]
		positions[idx+3] <- runif(1) # mass
	}

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- mapply(nbodyFunction, vx, vy, vz, px, py, pz);
		end <- nanotime()
		total <- end - start
		print(total)
	}
}

## Main
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

