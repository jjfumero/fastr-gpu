#! /usr/bin/Python

import os
import sys 

def setCurrentVersion():
	command = "cat mx.fastr/suite.py | grep -A1  marawacc | grep version > marawacc_version"
	os.system(command)

if __name__ == "__main__":
	setCurrentVersion()	
