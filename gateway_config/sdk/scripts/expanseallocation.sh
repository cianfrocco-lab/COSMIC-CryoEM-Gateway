#!/bin/sh
ssh -t -t -i ${expanse.keyFile} cosmic2@login.expanse.sdsc.edu \
        "source ${expanse.rc}; expanse-client project csd547 -r expanse_gpu"  2> >(grep -v 'Shared connection to login.expanse.sdsc.edu closed.' 1>&2)
ssh -t -t -i ${expanse.keyFile} cosmic2@login.expanse.sdsc.edu \
        "source ${expanse.rc}; expanse-client project csd547 -r expanse"  2> >(grep -v 'Shared connection to login.expanse.sdsc.edu closed.' 1>&2)
ssh -t -t -i ${expanse.keyFile} cosmic2@login.expanse.sdsc.edu \
        "source ${expanse.rc}; expanse-client user cosmic2 -r expanse_gpu"  2> >(grep -v 'Shared connection to login.expanse.sdsc.edu closed.' 1>&2)
ssh -t -t -i ${expanse.keyFile} cosmic2@login.expanse.sdsc.edu \
        "source ${expanse.rc}; expanse-client user cosmic2 -r expanse"  2> >(grep -v 'Shared connection to login.expanse.sdsc.edu closed.' 1>&2)
