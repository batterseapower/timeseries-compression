import numpy as np
import pandas as pd
import ctypes
from datetime import date
from matplotlib import pyplot as plt

def f2l(x):
	return ctypes.cast((ctypes.c_float*1)(x), ctypes.POINTER(ctypes.c_int)).contents.value

vod = pd.read_csv('VOD.csv').set_index('Date')
vod.index = pd.DatetimeIndex(vod.index)

xs = vod.reindex(pd.bdate_range(min(vod.index), max(vod.index)))['Adj Close']
xs = [(y / x) - 1.0 for x, y in zip(xs, xs[1:])] # Returns
xs = filter(lambda x: x != 0.0 and x == x, xs) # Sane values only 0s/NaNs
xs = map(f2l, xs)

df = pd.DataFrame.from_dict({
		'sign':     [(x & 0x80000000) >> 31 for x in xs],
		'exponent': [(x & 0x7F800000) >> 23 for x in xs],
		'mantissa': [(x & 0x00777777) >>  0 for x in xs],
	})

for c in ['sign', 'exponent', 'mantissa']:
	df[c].plot(title=c)
	plt.show()

mantissa_delta = pd.Series([x - y for x, y in zip(df.mantissa[1:], df.mantissa)])

mantissa_delta.plot(title='Mantissa Delta')
plt.show()

mantissa_delta.plot(kind='kde', title='Distribution of Mantissa Delta')
plt.show()
