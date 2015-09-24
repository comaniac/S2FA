#include <stdio.h>
#include <stdlib.h>

#include "blaze.h" 

using namespace blaze;

class SimpleAddition : public Task {
public:

  // extends the base class constructor
  // to indicate how many input blocks
  // are required
  SimpleAddition(): Task(2) {;}

  // overwrites the compute function
  virtual void compute() {

    // get input data length
    int data_length = getInputLength(0);

    // get the pointer to input/output data
    double* a = (double*)getInput(0);
		double val = (double) *(reinterpret_cast<long*>(getInput(1)));
    double* b = (double*)getOutput(0, 1, data_length, sizeof(double));

    // perform computation
    for (int i=0; i<data_length; i++) {
      b[i] = a[i] + val;
    }

    // if there is any error, throw exceptions
  }
};

extern "C" Task* create() {
  return new SimpleAddition();
}

extern "C" void destroy(Task* p) {
  delete p;
}