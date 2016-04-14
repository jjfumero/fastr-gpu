default: parallel

all:
	python scripts/diffUpdate.py
	mx build 

parallel:
	mx sforceimports
	mx build -p

update:
	mx sforceimports

eclipse:
	mx eclipseinit 

clean:
	mx clean


