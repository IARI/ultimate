int nondet() { int a; return a; }
_Bool nondet_bool() { _Bool a; return a; }
int main() {
int v1 = nondet();
int v2 = nondet();
int v3 = nondet();
int v4 = nondet();
int v5 = nondet();
int v6 = nondet();
int v7 = nondet();
int v8 = nondet();
int v9 = nondet();
int v10 = nondet();
int v11 = nondet();
int v12 = nondet();
int v13 = nondet();
int v14 = nondet();
int v15 = nondet();
int v16 = nondet();
int v17 = nondet();
int v18 = nondet();
int v19 = nondet();
int v20 = nondet();
int v21 = nondet();
int v22 = nondet();
int v23 = nondet();
int v24 = nondet();
int v25 = nondet();
int v26 = nondet();
goto loc_15;
loc_15:
 if (nondet_bool()) {
  goto loc_14;
 }
 goto end;
loc_CP_3:
 if (nondet_bool()) {
  goto loc_13;
 }
 goto end;
loc_CP_10:
 if (nondet_bool()) {
  goto loc_11;
 }
 goto end;
loc_0:
 if (nondet_bool()) {
  goto loc_1;
 }
 goto end;
loc_2:
 if (nondet_bool()) {
  goto loc_CP_3;
 }
 goto end;
loc_4:
 if (nondet_bool()) {
  if (!( v5 <= 0 )) goto end;
  goto loc_2;
 }
 if (nondet_bool()) {
  if (!( 1 <= v5 )) goto end;
  v26 = 1;
  v26 = 0;
  goto loc_2;
 }
 goto end;
loc_5:
 if (nondet_bool()) {
  v18 = 0;
  v5 = nondet();
  goto loc_4;
 }
 goto end;
loc_6:
 if (nondet_bool()) {
  goto loc_5;
 }
 goto end;
loc_7:
 if (nondet_bool()) {
  if (!( 1+v23 <= 2 )) goto end;
  goto loc_6;
 }
 if (nondet_bool()) {
  if (!( 3 <= v23 )) goto end;
  goto loc_6;
 }
 if (nondet_bool()) {
  if (!( 2 <= v23 )) goto end;
  if (!( v23 <= 2 )) goto end;
  v9 = v1;
  v18 = 1+v18;
  goto loc_6;
 }
 goto end;
loc_8:
 if (nondet_bool()) {
  v3 = 0;
  goto loc_7;
 }
 goto end;
loc_9:
 if (nondet_bool()) {
  if (!( 1 <= v23 )) goto end;
  if (!( v23 <= 1 )) goto end;
  v16 = 1+v16;
  goto loc_CP_10;
 }
 if (nondet_bool()) {
  if (!( 1+v23 <= 1 )) goto end;
  goto loc_8;
 }
 if (nondet_bool()) {
  if (!( 2 <= v23 )) goto end;
  goto loc_8;
 }
 goto end;
loc_12:
 if (nondet_bool()) {
  if (!( v1 <= 0 )) goto end;
  v10 = v1;
  v11 = v3;
  v25 = nondet();
  v20 = v25;
  v7 = v20;
  v19 = 1;
  v19 = 0;
  v23 = v7;
  goto loc_9;
 }
 if (nondet_bool()) {
  if (!( 1 <= v1 )) goto end;
  goto loc_5;
 }
 goto end;
loc_13:
 if (nondet_bool()) {
  goto loc_CP_3;
 }
 goto end;
loc_11:
 if (nondet_bool()) {
  if (!( 1+v16 <= v4 )) goto end;
  v8 = v17;
  v12 = v2;
  v13 = v15;
  v14 = v18;
  v24 = nondet();
  v21 = v24;
  v6 = v21;
  v1 = v6;
  goto loc_12;
 }
 if (nondet_bool()) {
  if (!( v4 <= v16 )) goto end;
  goto loc_5;
 }
 goto end;
loc_14:
 if (nondet_bool()) {
  v26 = 0;
  v22 = v26;
  v22 = 1;
  v22 = 0;
  goto loc_CP_10;
 }
 goto end;
loc_1:
end:
;
}
