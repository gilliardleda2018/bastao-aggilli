CORE := ../lib/bastao_core
CXXFLAGS := -std=c++11 -Wall -Wextra -Werror -O1 -g -fsanitize=address,undefined -I$(CORE)

test: test_core
	./test_core

test_core: test_core.cpp $(CORE)/tag_parser.cpp $(CORE)/tag_parser.h $(CORE)/reading_queue.h
	$(CXX) $(CXXFLAGS) -o $@ test_core.cpp $(CORE)/tag_parser.cpp

clean:
	rm -f test_core

.PHONY: test clean
