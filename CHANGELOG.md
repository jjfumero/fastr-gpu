

## astx-0.3
	* Basic support for GPU from AST -> Graal IR with Truffle information -> OpenCL code.
      This is just one example workin (examples/gpu/testGPU.R) 
	* NA assumptions. There is no GPU code support for NA numbers. 
	* Depth assumption is 0 -> It is removed from the code generator.
	* Graph simplification phases. It removes FrameState nodes from the original graph.
	* Boxing and UnBoxing elimination. 

## astx-0.21
	* Basic support for kernel generation (simple expressions) and non-generic.
      This is a proof of concept.
	* Interception from the Partial Evaluator to the GPU backend
	* Mechanism to identify a potencial R callTarget for GPU compilation

## astx-0.2
	* Support async for map operations : get((map(map(map))))
	* Support async for reduction operations
	* Get node added
	* Futures added for async support
	* Change number of threads per expression

## astx-0.1
	* Support for map/reduce with Marawacc threads backend. 
	* Support for Integer, Boolean and Double arrays
	* Function composition from R.


