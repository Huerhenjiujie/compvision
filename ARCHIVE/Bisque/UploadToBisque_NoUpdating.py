import sys
sys.path.insert(0, "/mnt/data27/wisser/drmaize/compvision/Bisque/Includes")

import os,requests,re, time
from itertools import izip
from Upload.UploadHandler import UploadHandler
from DB.DBHandler import DBHandler
from Util.TwitterClient import TwitterClient

use_twitter = True

# For iterating over pairs of elements
def pairwise(iterable):
    "s -> (s0, s1), (s2, s3), (s4, s5), ..."
    a = iter(iterable)
    return izip(a, a)

def UploadToBisque():

	if len(sys.argv) < 3:
		print "Usage: python UploadToBisque.py <image_file> <dataset_name> [<meta_tag> <meta_data> ...]"
	else:
	
		#Set semi-globals
		reconnect_time = 5
		reconnection_attempts = 5
	
		image_file = sys.argv[1].replace('\'','').encode("utf8")
		dataset_name = sys.argv[2]
		inp_metadata = sys.argv[3:]
		
		if len(inp_metadata) % 2 != 0:
			print "Error: Metadata needs to be passed in pairs (%2 == 0)"
			return
		
		print "**************************"
		print "\nPreparing to upload file", image_file
		
		# Setup metadata
		metadata = {}
		for tag, data in pairwise(inp_metadata):
			metadata[str(tag)] = str(data.replace('\'',''))
		
		# Setup BisqueHandlers
		script_path = os.path.dirname(os.path.realpath(__file__))
		config_path = os.path.join(script_path, ".ConfigOptions")
		uh = UploadHandler(config_file=config_path, debug=True)
		dbh = DBHandler(config_file=config_path, debug=True)
		tclient = TwitterClient(config_file=config_path)
		
		#######################################################
		## 1) Check to see all parameters are included for DB #
		#######################################################
		
		#
		## Example File Name:
		#
		## e013SLBp01wA1x20_1506111930rc001.ome.tif
		#
		
		# Set the Inventory DB info
		inventory_table = 'inventory'
		inventory_key = 'sample'
		inventory_headers = dbh.get_columns(inventory_table)
		
		# Set the MicroImage DB info
		microImage_table = 'microImage'
		microImage_key = 'microImage_id'
		microImage_headers = dbh.get_columns(microImage_table)
		
		# Set the MicroImageSet DB info
		microImageSet_table = 'microImageSets'
		microImageSet_key = 'microImageSet_id'
		microImageSet_headers = dbh.get_columns(microImageSet_table)
		
		# Get info from filename
		file_basename = os.path.basename(image_file)
		regex = re.compile('(e)(.*?)(p)(.*?)(_.*?)(.....)\.')
		matches = regex.match(file_basename)
		dataset_name = matches.group(2)
		microImage_id = matches.group(1) + matches.group(2) + matches.group(3) + matches.group(4) + matches.group(5)
		reconstructedImage = microImage_id
		imageChannel = matches.group(6)
		microImageSet_id = reconstructedImage + imageChannel
		
		# Add in filename info to argument dictionary
		metadata['dataset'] = dataset_name
		metadata['microImage_id'] = microImage_id
		metadata['microImageSet_id'] = microImageSet_id
		metadata['reconstructedImage'] = reconstructedImage
		metadata['imageChannel'] = imageChannel
		
		# Add in Bisque Data
		metadata['bisqueURI'] = "NULL"
		metadata['bisqueText'] = "NULL"
		metadata['bisqueGobj'] = "NULL"
		
		print "\nMetadata: "
		for k,v in metadata.iteritems():
			print k + ": " + v
		
		# Combine lists w/o duplicates
		all_headers = list(set(inventory_headers + microImage_headers + microImageSet_headers))
		
		# Check that all headers are satisfied
		for header in all_headers:
			if header not in metadata.keys():
				print ">>> Error! Header " + header + " was not included... exiting"
				return
				
		print "All arguments are included!\n"
		
		#######################################################
		## 2) Search for this entry in the Inventory Database #
		#######################################################
		
		# Search for existing entry
		search_dict = {inventory_key:metadata[inventory_key]}
		try:
			print "Searching for: ", search_dict
			inventory_row = dbh.search_col(inventory_table, search_dict, mode=4)[0]
			print "=> inventory entry", inventory_row
			
		except IndexError:
			
			# If entry doesn't exist, attempt to add entry
			print ">>> inventory entry doesn't exist... adding"
			row_list = []
			for header in inventory_headers:
				row_list.append(metadata[str(header)])
			#print row_list
			dbh.insert_into(inventory_table, row_list)
		
		print ""		
		########################################################
		## 3) Search for this entry in the MicroImage Database #
		########################################################
		
		# Search for existing entry
		search_dict = {microImage_key:metadata[microImage_key]}
		try:
			print "Searching for: ", search_dict
			microImage_row = dbh.search_col(microImage_table, search_dict, mode=4)[0]
			print "=> microImage entry", microImage_row
			
		except IndexError:
		
			# If entry doesn't exist, attempt to add entry
			print ">>> microImage entry doesn't exist... adding"
			row_list = []
			for header in microImage_headers:
				row_list.append(metadata[str(header)])
			#print row_list
			dbh.insert_into(microImage_table, row_list)
		
		print ""
		############################################################
		## 4) Search for this entry in the MicroImageSets Database #
		############################################################
		
		
		# Search for existing entry
		search_dict = {microImageSet_key:metadata[microImageSet_key]}
		try:
			print "Searching for: ", search_dict
			microImageSet_row = dbh.search_col(microImageSet_table, search_dict)[0]
			print "=> microImageSet entry", microImageSet_row
		except IndexError:
			
			# If entry doesn't exist, attempt to add entry
			print ">>> microImageSet entry doesn't exist... adding"
			row_list = [microImageSet_id, reconstructedImage, imageChannel, 'NULL', 'NULL', 'NULL']
			#print row_list
			dbh.insert_into(microImageSet_table, row_list)
			
		print ""
		
		##################################################
		## 5) Upload file and obtain retval (Bisque URI) #
		##################################################
		
		uri = 'NULL'; # uri should never get added as "NULL"
		for attempts in range(reconnection_attempts):
			try:
				print "Uploading File", image_file
				print sys.path
				retval = uh.upload_image(image_file, metadata=metadata)
				uri = retval[1];
				print ">>> BisqueURI is ", uri
				print ""

				break;
			except Exception,e:
				print ">>> Error: " + str(e)
				print ">>> Could not upload... trying again... (" + str(attempts+1) + "/" + str(reconnection_attempts) + ")"
				for i in range(reconnect_time):
					print "Retrying in... ", reconnect_time-i
					time.sleep(1)
		else:
			print "\n********************"
			print ">>> Unable to upload (timeout)... "
			print "Command to retry: "
			print "python", " ".join(sys.argv)
			print ">>> Exiting..."
			print "********************\n"				
			return

		if use_twitter:
			tweet = reconstructedImage + " Uploaded! "
			tweet += "Bisque link: http://bisque.iplantcollaborative.org/client_service/view?resource="
			tweet += uri + " "
			tweet += "#DrMaize"
			tclient.tweet(tweet)
			
		print "Updating microImageSets entry with Bisque URI... "
		set_dict = {'bisqueURI':uri}		
		dbh.update_entry(microImageSet_table, set_dict, search_dict)			
			
		print "\n*************************************************"
		print "* Addition to drmaizeIDB and Bisque Successful! *"
		print "*************************************************\n"

if __name__ == "__main__":
	requests.packages.urllib3.disable_warnings()
	UploadToBisque() 
		
	
	# Sample Line to run tests with...
	'''
	python /mnt/data27/wisser/drmaize/compvision/Bisque/UploadToBisque.py /mnt/data27/wisser/drmaize/image_data/e013SLB/microimages/reconstructed/HS/e013SLBp01wA1x20_1506111930rf001.ome.tif TEST_DATASET sample s1 experiment exp plate 1 well A tissue t receivedWhen time receivedFrom me disease SLB pathogenStrain AGoodOne hostAccession dk_what_this_is hpi or_this leafNumber 1 replication yes treatment immediately inventory_comments none microImage_id SOMETHING sample s1 microImageStart here microImageStop there microImage something.tiff microMIP MIPimage imagingDimensions 36_24_36 imagingDirection DueNorth magnification 20x microImage_comments sure tilingStatus complete!


	'''	
	
