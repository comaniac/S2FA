#include <stdio.h>
#include <stdlib.h>

#include "blaze.h" 

using namespace blaze;

class MonteCarlo : public Task {
public:

  // extends the base class constructor
  // to indicate how many input blocks
  // are required
  MonteCarlo(): Task(1) {;}

  // overwrites the compute function
  virtual void compute() {

    // get input data length
    int data_length = getInputLength(0);

    // get the pointer to input/output data
    int* a = (int*)getInput(0);
    int* b = (int*)getOutput(0, 1, 1, sizeof(double));

    // perform computation
    for (int i=0; i<data_length; i++) {
      *b += 1;
    }

    // if there is any error, throw exceptions
  }
};

extern "C" Task* create() {
  return new MonteCarlo();
}

extern "C" void destroy(Task* p) {
  delete p;
}
