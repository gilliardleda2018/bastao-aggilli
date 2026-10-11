CORE := ../lib/bastao_core
CXXFLAGS := -std=c++11 -Wall -Wextra -Werror -O1 -g -fsanitize=address,undefined -I$(CORE)
SRCS := test_core.cpp test_logic.cpp $(CORE)/tag_parser.cpp
HDRS := $(CORE)/tag_parser.h $(CORE)/reading_queue.h $(CORE)/persist_log.h $(CORE)/bastao_logic.h

test: test_core
	./test_core

test_core: $(SRCS) $(HDRS)
	$(CXX) $(CXXFLAGS) -o $@ $(SRCS)

clean:
	rm -f test_core

.PHONY: test clean
