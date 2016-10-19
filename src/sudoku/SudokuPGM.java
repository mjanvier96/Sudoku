package sudoku;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Date;

public class SudokuPGM {
	public static int PROPAGATION_Size = 0;
	public int[][] matrix = new int[9][9];
	public int[] S = new int[81]; // scan from first row, then second row
	public int[][] N_Current = new int[27][9];
	public int[][] N = new int[27][9];// [index of constraint][cell index]
	public int[][] M = new int[81][3];// [index of S][constraint index]
	public double[][] P = new double[81][9]; //
	public double[][][] R = new double[27][81][9];
	public double[][][] Q = new double[27][81][9];
	public double[][] R_P = new double[81][9];
	public int SUDOKU = 0;
	public int incompleteCount = 81;
	public int success = 0;
	public int fail = 0;
	public static void main(String args[]) {
		String pathname = "sudoku_left";
		for (int i = 18; i <= 40; i++) { // set the MP round size
			Date start = new Date();
			SudokuPGM sudoku = new SudokuPGM();
			SudokuPGM.PROPAGATION_Size = i;
			sudoku.read(pathname);
			System.out.println("\n\nSuccess: " + sudoku.success);
			System.out.println("Fail: " + sudoku.fail);
			Date end = new Date();
			System.out.println(end.getTime() - start.getTime());
		}
	}
	// initialize the matrix, S, N, M
	public void initialMatrix() {
		// read from file
		incompleteCount = 81;
		int index = 0;
		for (int i = 0; i < 27; i++) {
			for (int j = 0; j < 9; j++) {
				N_Current[i][j] = -1;
				N[i][j] = -1;
			}
		}
		for (int i = 0; i < 9; i++) {
			for (int j = 0; j < 9; j++) {
				S[index] = matrix[i][j];
				int boxN = getBoxNumber(i, j);
				M[index][0] = i;
				M[index][1] = j + 9;
				M[index][2] = boxN;
				addToN(index, i);
				addToN(index, j + 9);
				addToN(index, boxN);
				// only store the real values of index into its constraints N
				if (S[index] != 0) {
					modifyN_Current(index);
				}
				for (int v = 0; v < 9; v++) {// initialize the probability of
					// 1-9 to 1
					for (int C = 0; C < 3; C++) {
						Q[M[index][C]][index][v] = Math.log10(1);
						R[M[index][C]][index][v] = Math.log10(1);
					}
				}
				index++;
			}
		}
	}
	public void read(String pathname) {
		try {
			System.out.println("MP Round: " + PROPAGATION_Size);
			File filename = new File(pathname);
			InputStreamReader reader = new InputStreamReader(
					new FileInputStream(filename));
			BufferedReader br = new BufferedReader(reader);
			String line = "";
			line = br.readLine();
			int i = 0;
			while (line != null) {
				if (!line.contains("#")) {
					String[] m = line.split(" ");
					for (int j = 0; j < 9; j++) {
						matrix[i % 9][j] = Integer.parseInt(m[j]);
					}
					if (i % 9 == 8) {
						SUDOKU += 1;
						System.out.print("P" + SUDOKU + ": ");
						initialMatrix();
						int setCount = 1;// loop until there is no updating in
						// one round
						while (setCount > 0) {
							setCount = eliminate();
							// System.out.print("SetCount:"+setCount);
						}
						if (incompleteCount > 0) {
							for (int n = 0; n < 81; n++) {
								if (S[n] == 0)
									getProbability(n);
							}
							propagation();
							int go = 1;
							int loop = incompleteCount;
							for (int g = 1; g <= loop && go > 0; g++) {
								go = guess();
							}
						}
						if (incompleteCount == 0) {
							success++;
							System.out.println(" Success!");
						} else {
							fail++;
							System.out.println(" Fail! " + incompleteCount
									+ " unset cells.");
						}
					}
					i++;
				}
				line = br.readLine();
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	// return 1 if success; return -1 if fail
	public int guess() {
		// printP();
		double maxP = Math.log10(0);
		int maxIndex = 0;
		// boolean flagg = true;
		for (int n = 0; n < 81; n++) {// find the max p one
			if (S[n] == 0) {
				for (int v = 0; v < 9; v++) {
					if (P[n][v] > Math.log10(0) && P[n][v] < Math.log10(1)) {
						if (P[n][v] > maxP) {
							maxP = P[n][v];
							maxIndex = n;
						}
					}
				}
			}
		}
		if (S[maxIndex] != 0) {
			return -1;
		}
		// go to deal with the max one
		double maxRP = Math.log10(0);
		int value = 0;
		int[] relatedCells = new int[20];
		int r_index = 0;
		for (int i = 0; i < 20; i++) {
			relatedCells[i] = -1;
		}
		for (int C = 0; C < 3; C++) {// in 3 constriants
			int m = M[maxIndex][C];
			for (int i = 0; i < 9; i++) {// 9 cells
				if (S[N[m][i]] == 0) {
					boolean flag = true;
					for (int ii = 0; ii <= (r_index - 1) && flag; ii++) {
						if (relatedCells[ii] == N[m][i]) {
							flag = false;
						}
					}
					if (flag) {
						relatedCells[r_index] = N[m][i];
						r_index++;
					}
				}
			}
		}
		// System.out.println("\nFor: " + maxIndex);
		for (int v = 0; v < 9; v++) {// find other possible values
			if (P[maxIndex][v] > Math.log10(0)) {// compute Relative probability
				double sum = Math.log10(0);
				int i = 0;
				while (relatedCells[i] != -1) {
					sum = sum_log(sum, P[relatedCells[i]][v]);
					i++;
				}
				R_P[maxIndex][v] = P[maxIndex][v] - sum;
				if (R_P[maxIndex][v] > maxRP) {
					maxRP = R_P[maxIndex][v];
					value = v + 1;
				}
			}
		}
		if (value == 0) {
			return -1;
		}
		S[maxIndex] = value;
		modifyN_Current(maxIndex);
		for (int v = 0; v < 9; v++) {// update the current cell
			P[maxIndex][v] = Math.log10(0);
		}
		for (int i = 0; i < 20; i++) {
			relatedCells[i] = -1;
		}
		r_index = 0;
		for (int C = 0; C < 3; C++) {// in 3 constriants
			int m = M[maxIndex][C];
			for (int i = 0; i < 9; i++) {// 9 cells
				if (S[N[m][i]] == 0 && P[N[m][i]][value - 1] > Math.log10(0)
						&& N[m][i] != maxIndex) { // right cell
					boolean flag = true;
					for (int ii = 0; ii <= (r_index - 1) && flag; ii++) {
						if (relatedCells[ii] == N[m][i]) {
							flag = false;
						}
					}
					if (flag) {
						relatedCells[r_index] = N[m][i];
						r_index++;
					}
				}
			}
		}
		int i = 0;
		while (relatedCells[i] != -1) {
			int num = 0;
			for (int v = 0; v < 9; v++) { // possible values
				if (P[relatedCells[i]][v] > Math.log10(0)) {
					num++;
				}
			}
			double distribute = P[relatedCells[i]][value - 1]
					- Math.log10(num - 1);
			P[relatedCells[i]][value - 1] = Math.log10(0);// uodate that cell
			for (int v = 0; v < 9; v++) {
				if (P[relatedCells[i]][v] > Math.log10(0)) {
					sum_log(P[relatedCells[i]][v], distribute);
				}
			}
			i++;
		}
		return 1;
	}
	public void propagation() {
		for (int loop = 0; loop < PROPAGATION_Size; loop++) {
			messagePassing();
		}
	}
	public void messagePassing() {
		// R[m][n][x]=* # (1 - q[m][n'][value]) other 0 cell related in the same C
		for (int n = 0; n < 81; n++) {
			if (S[n] == 0) {
				for (int x = 0; x < 9; x++) {
					if (P[n][x] != Math.log10(0)) {// possible values!
						for (int C = 0; C < 3; C++) {
							int m = M[n][C];
							R[m][n][x] = Math.log10(1);
							String vp = getPossibleVP_R(m, n, x);
							String[] possibleVP = vp.split(",");
							String v = possibleVP[0];
							String[] p = possibleVP[1].trim().split(" ");
							double sum = permutate(n, "", v, p, Math.log10(0),
									m);
							R[m][n][x] = sum;
						}// R
					}
				}
			}// S indexS == 0
		}// indexS
		// Q[m][n][x]= P(n=x) * # R[m'][n][x], m'= other two
		for (int n = 0; n < 81; n++) {
			if (S[n] == 0) {
				for (int x = 0; x < 9; x++) {
					if (P[n][x] != Math.log10(0)) {// possible values!
						for (int C = 0; C < 3; C++) {
							int m = M[n][C];
							Q[m][n][x] = P[n][x];// P(n=x)
							for (int C_other = 0; C_other < 3; C_other++) {
								if (C_other != C) {
									int m_2 = M[n][C_other];
									Q[m][n][x] = Q[m][n][x] + R[m_2][n][x];
								}
							}
							// Q[m][n][x] = Math.log10(Q[m][n][x]);
						}
					}
				}
			}// S indexS == 0
		}// indexS
		// P[n][x]
		for (int n = 0; n < 81; n++) {
			if (S[n] == 0) {
				for (int x = 0; x < 9; x++) {
					if (P[n][x] != Math.log10(0)) {// possible values!
						for (int C = 0; C < 3; C++) {
							P[n][x] = P[n][x] + R[M[n][C]][n][x];
						}
						// P[n][x] = Math.log10(P[n][x]);
					}
				}
			}// S indexS == 0
		}// indexS
	}
	public double permutate(int n, String pre, String last, String[] position,
			double sum, int m) {
		if (last.length() == 0) {
			double product = Math.log10(1);
			for (int i = 0; i < pre.length(); i++) {
				int value = Integer.parseInt(pre.substring(i, i + 1));
				int indexS = Integer.parseInt(position[i]);
				product = product + Q[m][indexS][value];
				// if(n==66){System.out.println("Permutation: "+indexS+":"+value+" P:"+P[indexS][value]);}
			}
			sum = sum_log(sum, product);
			// if(n==66){System.out.println(product+" Product-SUM: "+sum);}
			return sum;
		}
		for (int i = 0; i < last.length(); i++) {
			sum = permutate(n, pre + last.substring(i, i + 1),
					last.substring(0, i) + last.substring(i + 1), position,
					sum, m);
		}
		return sum;
	}
	public String getPossibleVP_R(int m, int indexS, int value) {
		String result = "";
		for (int i = 0; i < 9; i++) {
			boolean flag = true;
			int index_NC = 0;
			while (N_Current[m][index_NC] != -1 && flag) {
				if ((i + 1) == S[N_Current[m][index_NC]]) {
					flag = false;
				}
				index_NC++;
			}
			if (flag && i != value) {
				result += String.valueOf(i);
			}
		}
		result += ",";
		for (int i = 0; i < 9; i++) {
			boolean flag = true;
			int index_NC = 0;
			while (N_Current[m][index_NC] != -1 && flag) {
				if (N[m][i] == N_Current[m][index_NC]) {
					flag = false;
				}
				index_NC++;
			}
			if (flag && N[m][i] != indexS) {
				result += String.valueOf(N[m][i]);
				result += " ";
			}
		}
		return result.trim();
	}
	// get probability from 3 constraints related to a node
	public void getProbability(int index) {
		for (int i = 0; i < 9; i++) {// initialize the probability of 1-9 to 1
			P[index][i] = Math.log10(1);
		}
		for (int C = 0; C < 3; C++) {
			getProbabilityFromEachConstraints_PRODUCT(index, M[index][C]);
		}
		int vCount = 0;
		for (int i = 0; i < 9; i++) {
			if (P[index][i] > Math.log10(0)) {
				vCount++;
			}
		}
		if (vCount != 0) {
			for (int i = 0; i < 9; i++) {
				P[index][i] = P[index][i] - Math.log10(vCount);
			}
		}
		getProbabilityForQ(index);
		// Q[[M[index][0]][index][0-8]=0;
	}
	// c1,c2: constraints number
	public void getProbabilityForQ(int indexS) {
		for (int C = 0; C < 3; C++) {
			for (int v = 0; v < 9; v++) {
				Q[M[indexS][C]][indexS][v] = Math.log10(1);
			}
			for (int C_other = 0; C_other < 3; C_other++) {
				if (C_other != C) { // only concern about other two constraints
					getProbabilityFromEachConstraints_Q(indexS, M[indexS][C],
							M[indexS][C_other]);
				}
			}
			int vCount = 0;
			for (int i = 0; i < 9; i++) {
				if (Q[M[indexS][C]][indexS][i] > Math.log10(0)) {
					vCount++;
				}
			}
			if (vCount != 0) {
				for (int i = 0; i < 9; i++) {
					Q[M[indexS][C]][indexS][i] = Q[M[indexS][C]][indexS][i]
							- Math.log10(vCount);
				}
			}
		}
	}
	public void getProbabilityFromEachConstraints_Q(int index, int m,
			int realConstraintNum) {
		int num = 0;
		while (N_Current[realConstraintNum][num] != -1) {
			int indexS = N_Current[realConstraintNum][num];
			Q[m][index][S[indexS] - 1] = Math.log10(0);
			num++;
		}
	}
	public void getProbabilityFromEachConstraints_PRODUCT(int index,
			int constraintNum) {
		int num = 0;// get the number of values in the constraints
		while (N_Current[constraintNum][num] != -1) {
			int indexS = N_Current[constraintNum][num];// index of S
			// System.out.println(indexS + ": " + S[indexS]);
			P[index][S[indexS] - 1] = Math.log10(0);
			num++;
		}
	}
	// add value to all the constrains
	public void modifyN_Current(int index) {
		incompleteCount -= 1;
		for (int C = 0; C < 3; C++) {
			addToN_Current(M[index][C], index);
		}
	}
	public void addToN(int indexS, int N_number) {
		int index = 0;
		while (N[N_number][index] != -1) {// find the next place to put new
			index++;
		}
		N[N_number][index] = indexS;
	}
	// add real value to one constraint
	public void addToN_Current(int constraintNum, int indexS) {
		int index = 0;
		while (N_Current[constraintNum][index] != -1) {
			// System.out.println(SUDOKU+""+constraintNum+"]["+index+":"+N_Current[constraintNum][index]+
			// "Want to put"+indexS+":"+S[indexS]);
			index++;
		}
		N_Current[constraintNum][index] = indexS;
	}
	public int getBoxNumber(int i, int j) {
		int number = 0;
		if (i % 9 <= 2) {
			number = 18;
		} else if (i % 9 <= 5) {
			number = 21;
		} else {
			number = 24;
		}
		if (j % 9 >= 3 && j % 9 <= 5) {
			number += 1;
		} else if (j % 9 > 5) {
			number += 2;
		}
		return number;
	}
	public void print() {
		int e = 0;
		for (int i = 0; i < 9; i++) {
			for (int j = 0; j < 9; j++) {
				System.out.print(S[e++] + " ");
			}
			System.out.println();
		}
		System.out.println();
	}
	// loop to get the probability
	public int eliminate() {
		int setCount = 0; // count the number of values set in this round
		double maxP = Math.log10(0);
		int targetIndex = -1;
		int targetValue = 0;
		for (int i = 0; i < 81; i++) {
			if (S[i] == 0) {
				getProbability(i);
				int count = 0; // number of values with probability != 0
				int value = 0; // when count == 1, this value is unique
				for (int j = 0; j < 9; j++) {
					if (P[i][j] != Math.log10(0)) {
						if (P[i][j] > maxP) {
							maxP = P[i][j];
							targetIndex = i;
							targetValue = j + 1;
						}
						count++;
						value = j + 1;
					}
				}
				if (count == 1) {
					S[i] = value;
					modifyN_Current(i);
					setCount++;
				}
			}
		}
		return setCount;
	}
	public void printP() {
		for (int i = 0; i < 81; i++) {
			for (int v = 0; v < 9; v++) {
				if (S[i] == 0 && P[i][v] > Math.log10(0))
					System.out.println("P[" + i + "][" + (v + 1) + "]" + " "
							+ Math.pow(10, P[i][v]));
			}
		}
	}
	public void printN() {
		System.out.println("Constraints: ");
		for (int i = 0; i < 27; i++) {
			System.out.print(i + " ");
			for (int j = 0; j < 9; j++) {
				System.out.print(N[i][j] + " ");
			}
			System.out.println();
		}
		System.out.println();
	}
	public void printM() {
		System.out.println("Cells: ");
		for (int i = 0; i < 81; i++) {
			System.out.print(i + " ");
			for (int j = 0; j < 3; j++) {
				System.out.print(M[i][j] + " ");
			}
			System.out.println();
		}
		System.out.println();
	}
	public double sum_log(double a, double b) {
		if (a == Double.NEGATIVE_INFINITY) {
			return b;
		} else if (b == Double.NEGATIVE_INFINITY) {
			return a;
		} else {
			double x, y, c = 0;
			if (a > b) {
				x = a;
				y = b;
			} else {
				x = b;
				y = a;
			}
			double decide = Math.pow(10, x - y);
			if ((decide + 1) == Double.POSITIVE_INFINITY) {// overflow
				c = x;
			} else {
				decide += 1;
				c = y + Math.log10(decide);
			}
			return c;
		}
	}
}