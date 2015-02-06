#
# Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# This "builds" a test package, resulting in a tar file,
# which is then loaded by the unit tests in TestRPackages.
# Note that the tar file root must be a single directoru but
# the name is unimportant because the install process takes the 
# package name from the DESCRIPTION. So we just use the "src" directory.

.PHONY: all

PKG_FILES = $(shell find src/ -type f -name '*')

PKG_TAR = lib/$(PACKAGE).tar

all: $(PKG_TAR)

$(PKG_TAR): $(PKG_FILES)
	mkdir -p lib
	tar cf $(PKG_TAR) src

clean:
	rm -f $(PKG_TAR)

