import ogr2osm as ogr2osm
from ogr2osm import TranslationBase, OsmDataWriter
from ogr2osm.osm_geometries import OsmId


def convert(options):
    # set start id for nodes
    OsmId.set_id(options.id, is_positive=False)

    # custom translation SHP tags to set LoMaps NoSea tags
    translation_object = CustomTranslation()

    # read source vector data
    datasource = ogr2osm.OgrDatasource(translation_object)
    datasource.open_datasource(options.input)

    osmdata = ogr2osm.OsmData(translation_object, start_id=options.id, is_positive=True)
    osmdata.process(datasource)

    # create datawriter and write OSM data
    datawriter = OsmDataWriter(options.output, add_version=True, add_timestamp=True)

    osmdata.output(datawriter)


class CustomTranslation(TranslationBase):

    def __init__(self):
        # this tags are set to all features converted to OSM
        self.nosea_tags = {
            'natural': 'nosea',
            'layer': '-4',
        }

    def filter_tags(self, tags):
        '''
        Override this method if you want to modify or add tags to the xml output
        '''
        return {**tags, **self.nosea_tags}  # merge original SHP tags with required no-sea tags
