#!/bin/tclsh
# developed by NSG developers
# should put WORKSPACE and job expression string into
# bash_profile or runit or commandline arg, so that
# this script can be used in both staging and production
# without modification
#set WORKSPACE /projects/cosmic2/gatewaydev/workspace
#puts "dollar colon colon env HOME ($::env(HOME))"
#puts "env HOME ($env(HOME))"
#puts "env HOME ($::env(WORKSPACE))"
#if {[info exists ::env(HOME)]} {
#  puts "HOME exists $::env(HOME)"
#} else {
#  puts "HOME does not exist"
#}
if {[info exists ::env(WORKSPACE)]} {
  set WORKSPACE $::env(WORKSPACE)
} else {
  puts "please set WORKSPACE environment variable"
  exit
}
puts "WORKSPACE ($WORKSPACE)"
#set joblist [glob $WORKSPACE/NGBW-JOB-{MATLAB,EEGLAB}*]
set joblist [glob $WORKSPACE/COSMIC2-JOB-*]
foreach workdir $joblist {
  puts ----------------------------------------------------------------
  puts "workdir ($workdir)"
  if [file exists $workdir/_JOBINFO.TXT] then {
    set fileid [open $workdir/_JOBINFO.TXT r]
    set filetext [read $fileid]
    set result FALSE
    set match FALSE
    set sub1 FALSE
    set result [regexp {job_properties \((.*)\).*gatewayuser} $filetext match sub1]
    #puts "result $result"
    #puts "sub1 $sub1"
    if $result then {
      set result FALSE
      set match FALSE
      set sub1 FALSE
      set sub2 FALSE
      #set result [regexp {JOBID: (\d+)} $filetext match sub1 sub2]
      #set result [regexp {'JOBID': '(\d+)'.*'email': '([^']+)'} $filetext match sub1 sub2]
      #set result [regexp {JOBID: (\d+).*'email': '([^']+)'} $filetext match sub1 sub2]
      #set result [regexp {JOBID: (\d+)} $filetext match sub1]
      set result [regexp {JOBID=(\d+).*'email': '([^']+)'} $filetext match sub1 sub2]
      #puts "JOBID sub1 $sub1"
      #puts "$filetext"
      puts "JOBID Result: $result Match: $match 1: $sub1 2: $sub2"
      set status 0
      if {[catch {exec squeue --job $sub1} results options]} {
        set details [dict get $options -errorcode]
        if {[lindex $details 0] eq "CHILDSTATUS"} {
          set status [lindex $details 2]
        } else {
          puts "non CHILDSTATUS error $results $options"
        }
      }
      puts "squeue Result: $results"
    } else {
      puts "Result: no match in ($workdir/_JOBINFO.TXT) ($filetext)"
    }
    close $fileid
  } else {
      puts "Result: no file ($workdir/_JOBINFO.TXT)"
  }
}
