CASES=aes nw kmeans logistic pagerank

all: ${CASES}
	@for f in $^; do make -C $$f/app; make -C $$f/app opt; done

test: ${CASES}
	@for f in $^; do make -C $$f/app test; done

clean: ${CASES}
	@for f in $^; do make -C $$f/app clean; done
