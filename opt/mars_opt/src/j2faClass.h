#include "codegen.h"
#include <map>               
#include <string>            
#include <stdlib.h>          
#include <stdio.h>           
 
class j2faClass 
{
	private:
		SgClassDefinition *sg_class;
		map <string, int> mapVar2Size;
		map <string, int> mapVar2Idx;
		int varSize; // The total size of variables
		int size; // The maximum size of this class and its derived classes

	public:
		j2faClass(SgClassDefinition *_class) {
			sg_class = _class;
			size = 1;
			varSize = 1;
			mapVar2Size["j2fa_clazz"] = 1;
			mapVar2Idx["j2fa_clazz"] = 0;
		}

		void SetSize(int s) { 
			// If the class has derived classes, we need to
			// record the max size of the derived class
			if (s > size)
				size = s; 
		}

		int GetSize() { return size; }

		bool HasVariable(string name) {
			if (mapVar2Size[name])
				return true;
			return false;
		}

		void AddVariable(string name, int _size) {
			mapVar2Size[name] = _size;
			varSize += _size;
			if (varSize > size)
				size = varSize;
		}

		int GetVariableIndex(string name) {
			if (mapVar2Idx[name])
				return mapVar2Idx[name];
			return -1;
		}

		void CalcIndex() {
			int idx = 1;
			map<string, int>::iterator vite = mapVar2Size.begin();
			for (; vite != mapVar2Size.end(); vite++) {
				if (vite->first == "j2fa_clazz")
					continue;
				mapVar2Idx[vite->first] = idx;
				idx += vite->second;
			}
		}

		void DumpVariables() {
			map<string, int>::iterator vite = mapVar2Idx.begin();
			for (; vite != mapVar2Idx.end(); vite++)
				cerr << vite->second << ":" << vite->first << endl;
		}
};
