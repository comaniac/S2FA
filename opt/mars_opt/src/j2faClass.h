#include "codegen.h"
#include <map>               
#include <string>            
#include <stdlib.h>          
#include <stdio.h>           
 
class j2faClass 
{
	private:
		SgClassDefinition *sg_class;
		int id;
		bool primaryIn;
		bool primaryOut;
		vector <j2faClass *> vecDerivedClses;
		vector <j2faClass *> vecBaseClses;
		map <string, int> mapVar2Size;
		map <string, int> mapVar2Idx;
		int varSize; // The total size of variables
		int size; // The maximum size of this class and its derived classes

	public:
		j2faClass(SgClassDefinition *_class, int _id) {
			id = _id;
			sg_class = _class;
			primaryIn = false;
			primaryOut = false;
			size = 0;
			varSize = 0;
		}

		void SetSize(int s) { 
			// If the class has derived classes, we need to
			// record the max size of the derived class
			if (s > size)
				size = s; 
		}

		int GetSize() { return size; }

		int GetID() { return id; }

		string GetName() { return sg_class->get_qualified_name(); }

		void SetAsPrimaryInputType() { primaryIn = true; }

		bool IsPrimaryIn() { return primaryIn;	}

		void SetAsPrimaryOutputType() { primaryOut = true; }

		bool IsPrimaryOut() { return primaryOut;	}

		void AddBaseClass(j2faClass *base) {
			if (mapVar2Size.find("j2fa_clazz") == mapVar2Size.end()) {
				// Add class ID for dynamic casting
				size++;
				varSize++;
				mapVar2Size["j2fa_clazz"] = 1;
				mapVar2Idx["j2fa_clazz"] = 0;
			}
			vecBaseClses.push_back(base);
		}

		bool HasBaseClass() {
			return (vecBaseClses.size())? true: false;
		}

		void AddDerivedClass(j2faClass *derived) {
			if (mapVar2Size.find("j2fa_clazz") == mapVar2Size.end()) {
				// Add class ID for dynamic casting
				size++;
				varSize++;
				mapVar2Size["j2fa_clazz"] = 1;
				mapVar2Idx["j2fa_clazz"] = 0;
			}
			vecDerivedClses.push_back(derived);
		}

		vector<j2faClass *> GetDerivedClasses() {
			return vecDerivedClses;
		}

		bool HasDerivedClass() {
			return (vecDerivedClses.size())? true: false;
		}

		bool HasFunc(string funcName) {
			SgDeclarationStatementPtrList &mList = sg_class->get_members();
			for (SgDeclarationStatementPtrList::iterator mite = mList.begin(); 
				mite != mList.end(); mite++) {
				SgMemberFunctionDeclaration *fun = isSgMemberFunctionDeclaration(*mite);
				if (!fun)
					continue;
				if (fun->get_qualified_name() == funcName)
					return true;
			}
			return false;
		}

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
			if (mapVar2Idx.find(name) != mapVar2Idx.end())
				return mapVar2Idx.find(name)->second;
			return -1;
		}

		int GetVariableSize(string name) {
			if (mapVar2Size.find(name) != mapVar2Size.end())
				return mapVar2Size.find(name)->second;
			return 0;
		}

		void CalcIndex() {
			int idx = 0;

			if (HasDerivedClass() || HasBaseClass())
				idx = 1;

			map<string, int>::iterator vite = mapVar2Size.begin();
			for (; vite != mapVar2Size.end(); vite++) {
				if (vite->first == "j2fa_clazz")
					continue;
				mapVar2Idx[vite->first] = idx;
				idx += vite->second;
			}
		}

		void DumpVariables() {
			cerr << "    Index:" << endl;
			map<string, int>::iterator vite = mapVar2Idx.begin();
			for (; vite != mapVar2Idx.end(); vite++)
				cerr << "      " << vite->second << ":" << vite->first << endl;

			cerr << "    Size:" << endl;
			vite = mapVar2Size.begin();
			for (; vite != mapVar2Size.end(); vite++)
				cerr << "      " << vite->first << ":" << vite->second << endl;
		}
};
