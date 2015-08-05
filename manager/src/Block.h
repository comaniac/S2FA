#ifndef BLOCK_H
#define BLOCK_H

#include <string.h>
#include <string>
#include <stdexcept>

#include <boost/smart_ptr.hpp>
#include <boost/iostreams/device/mapped_file.hpp>

/*
 * base class extendable to manage memory block
 * on other memory space (e.g. FPGA device memory)
 *
 */

namespace acc_runtime {

class DataBlock {

public:
  // create a single output elements
  DataBlock(int _length, int64_t _size):
    length(_length), 
    num_items(1),
    size(_size),
    allocated(true),
    ready(false)
  {
    //width = _size / _length;
    data = new char[_size];

  }

  DataBlock():
    length(0), num_items(0), size(0), width(0),
    allocated(false),
    ready(false),
    data(NULL)
  {
    ;  
  }

  ~DataBlock() {
    if (allocated && !data) {
      delete data; 
    }
  }

  virtual void alloc(int64_t _size) {

    size = _size;

    data = new char[size];

    allocated = true;
  }

  // copy data from an array
  virtual void writeData(void* src, size_t _size) {
    if (allocated) {
      memcpy((void*)data, src, _size);
      ready = true;
    }
    else {
      throw std::runtime_error("Block memory not allocated");
    }
  }

  // copy data from an array with offset
  virtual void writeData(void* src, size_t _size, size_t offset) {
    if (allocated) {
      if (offset+_size > size) {
        throw std::runtime_error("Exists block size");
      }
      memcpy((void*)(data+offset), src, _size);

      if (offset + _size == size) {
        ready = true;
      }
    }
    else {
      throw std::runtime_error("Block memory not allocated");
    }
  }

  // write data to an array
  virtual void readData(void* dst, size_t size) {
    if (allocated) {
      memcpy(dst, (void*)data, size);
    }
    else {
      throw std::runtime_error("Block memory not allocated");
    }
  }

  virtual char* getData() { 
    if (allocated) {
      return data; 
    }
    else {
      return NULL;
    }
  }

  void readFromMem(std::string path) {

    boost::iostreams::mapped_file_source fin;

    //int data_length = length; 
    int data_size = size;

    fin.open(path, data_size);

    if (fin.is_open()) {
      
      void* data = (void*)fin.data();

      try {
        writeData(data, data_size);

      } catch(std::runtime_error &e) {
        throw e;
      }

      fin.close();
    }
    else {
      throw std::runtime_error("Cannot find file");
    }
  }

  void writeToMem(std::string path) {

    //int data_length = length; 
    int data_size = size;

    printf("data size: %ld\n", size);

    boost::iostreams::mapped_file_params param(path); 
    param.flags = boost::iostreams::mapped_file::mapmode::readwrite;
    param.new_file_size = data_size;
    param.length = data_size;
    boost::iostreams::mapped_file_sink fout(param);

    if (fout.is_open()) {

      try {
        readData((void*)fout.data(), data_size);
      } catch(std::runtime_error &e) {
        throw e;
      }

      fout.close();
    }
    else {
      throw std::runtime_error("Cannot find file");
    }
  }

  int setLength(int _length) { 
    length = _length; 
  }

  int setNumItems(int _num) { 
    num_items = _num; 
  }

  int getLength() { return length; }

  int getNumItems() { return num_items; }

  int getSize() { return size; }

  bool isAllocated() { return allocated; }
  
  bool isReady() { return ready; }

protected:
  int length;       /* total number of elements */
  int num_items;    /* number of elements per data item */
  int width;        /* size of a single element */
  int64_t size;     /* byte size of all the data */
  bool allocated;
  bool ready;

private:
  char* data;
};

typedef boost::shared_ptr<DataBlock> DataBlock_ptr;

const DataBlock_ptr NULL_DATA_BLOCK;

}
#endif
