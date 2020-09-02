# Based on https://www.reneshbedre.com/blog/anova.html

import pandas
import statsmodels.api
from statsmodels.stats.diagnostic import kstest_normal
from statsmodels.formula.api import ols
from statsmodels.stats.multicomp import pairwise_tukeyhsd
import matplotlib.pyplot
import seaborn


def read_csv(filename):
    # load data file
    return pandas.read_csv("paste.csv", sep=",")


def melt_df(df):
    # reshape the d dataframe suitable for statsmodels package
    return pandas.melt(df.reset_index(), id_vars=['index'], value_vars=list(df.columns))


def describe(df):
    desc = df.describe()
    with open('desc.csv', 'w') as f:
        f.write(desc.to_csv() + '\n')


def do_ks(df_melt):
    # Kolmogorov-Smirnov test, whether sample variance distribution is normal
    ks = kstest_normal(df_melt['value'])
    with open('ks.csv', 'w') as f:
        f.write(str(ks) + '\n')


def do_anova(df_melt):
    # ANOVA
    model = ols('value ~ C(variable)', data=df_melt).fit()
    anova_table = statsmodels.api.stats.anova_lm(model, typ=2)
    with open('anova.csv', 'w') as f:
        f.write(anova_table.to_csv() + '\n')


def do_tukey(df_melt):
    # Tukey HSD test, if there is difference between groups
    # Reject === True means 'reject null hypothesis', there is difference for alpha=0.05
    tukey = pairwise_tukeyhsd(
        endog=df_melt['value'], groups=df_melt['variable'], alpha=0.05)
    with open('tukeyhsd.csv', 'w') as f:
        f.write(tukey.summary().as_csv() + '\n')


def do_catplot(df_melt):
    cp = seaborn.catplot(data=df_melt, kind="bar", x="value", y="variable")
    cp.set_axis_labels("Request elapsed time (the lower the better)", "Programming language")
    matplotlib.pyplot.gcf().set_size_inches(10, 5)
    matplotlib.pyplot.savefig('catplot.png')

def main():
    df = read_csv('paste.csv')
    describe(df)
    df_melt = melt_df(df)
    do_ks(df_melt)
    do_anova(df_melt)
    do_tukey(df_melt)
    do_catplot(df_melt)

if __name__ == "__main__":
    main()
